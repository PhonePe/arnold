package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroupCollection;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.MergeRequestGroupService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.devx.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupResponse;
import com.phonepe.devx.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Singleton
public class MergeRequestGroupServiceImpl implements MergeRequestGroupService {
    private final AerospikeMergeRequestGroupStore store;
    private final UserMergeRequestService userMergeRequestService;
    private final MergeQueueService mergeQueueService;

    @Inject
    public MergeRequestGroupServiceImpl(final AerospikeMergeRequestGroupStore store,
                                        final UserMergeRequestService userMergeRequestService,
                                        final MergeQueueService mergeQueueService) {
        this.store = store;
        this.userMergeRequestService = userMergeRequestService;
        this.mergeQueueService = mergeQueueService;
    }

    @Override
    public MergeRequestGroupCollectionResponse get(final String userId) {
        final var current = store.getWithVersion(userId);
        return current.map(value -> response(userId, value.value(), value.version()))
                .orElseGet(() -> new MergeRequestGroupCollectionResponse(List.of(), 0, 0));
    }

    @Override
    public MergeRequestGroupCollectionResponse create(final String userId,
                                                      final CreateMergeRequestGroupRequest request) {
        validateMembers(userId, request.getMergeRequestIds());
        final var groupId = UUID.randomUUID().toString();
        final var now = System.currentTimeMillis();
        final var result = mutate(userId, request.getVersion(), collection -> {
            final var groups = new ArrayList<>(collection.groups());
            groups.add(new MergeRequestGroup(groupId, request.getName().trim(),
                    distinct(request.getMergeRequestIds()), now, now));
            return new MergeRequestGroupCollection(userId, groups, now);
        });
        mergeQueueService.replaceMembersWithGroup(userId, groupId, request.getMergeRequestIds());
        return result;
    }

    @Override
    public MergeRequestGroupCollectionResponse update(final String userId,
                                                      final String groupId,
                                                      final UpdateMergeRequestGroupRequest request) {
        validateMembers(userId, request.getMergeRequestIds());
        final var now = System.currentTimeMillis();
        final var result = mutate(userId, request.getVersion(), collection -> {
            final var found = collection.groups().stream().anyMatch(group -> group.id().equals(groupId));
            if (!found) {
                throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
            }
            return new MergeRequestGroupCollection(userId, collection.groups().stream()
                    .map(group -> group.id().equals(groupId)
                            ? new MergeRequestGroup(group.id(), request.getName().trim(),
                            distinct(request.getMergeRequestIds()), group.createdAt(), now) : group)
                    .toList(), now);
        });
        mergeQueueService.replaceMembersWithGroup(userId, groupId, request.getMergeRequestIds());
        return result;
    }

    @Override
    public MergeRequestGroupCollectionResponse delete(final String userId,
                                                      final String groupId,
                                                      final long version) {
        final var result = mutate(userId, version, collection -> {
            if (collection.groups().stream().noneMatch(group -> group.id().equals(groupId))) {
                throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
            }
            return new MergeRequestGroupCollection(userId, collection.groups().stream()
                    .filter(group -> !group.id().equals(groupId)).toList(), System.currentTimeMillis());
        });
        mergeQueueService.removeGroup(userId, groupId);
        mergeQueueService.reconcile(userId, userMergeRequestService.associationsForUser(userId));
        return result;
    }

    private MergeRequestGroupCollectionResponse mutate(final String userId,
                                                       final long expectedVersion,
                                                       final UnaryOperator<MergeRequestGroupCollection> mutation) {
        final var current = store.getWithVersion(userId);
        if (current.isEmpty()) {
            if (expectedVersion != 0) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
            final var collection = mutation.apply(new MergeRequestGroupCollection(userId, List.of(), 0));
            try {
                store.create(collection);
                return response(userId, collection, 0);
            } catch (RuntimeException race) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
        }
        if (current.get().version() != expectedVersion) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        final var next = mutation.apply(current.get().value());
        try {
            store.checkAndUpdate(next, current.get().version());
            return response(userId, next, current.get().version() + 1);
        } catch (ConcurrentUpdateException conflict) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
    }

    private MergeRequestGroupCollectionResponse response(final String userId,
                                                         final MergeRequestGroupCollection collection,
                                                         final long version) {
        final var memberIds = collection.groups().stream().flatMap(group -> group.mergeRequestIds().stream())
                .distinct().toList();
        final var summaries = userMergeRequestService.summaries(userId, memberIds);
        final var groups = collection.groups().stream()
                .map(group -> new MergeRequestGroupResponse(group.id(), group.name(),
                        group.mergeRequestIds().stream().map(summaries::get)
                                .filter(java.util.Objects::nonNull).toList(),
                        group.createdAt(), group.updatedAt()))
                .toList();
        return new MergeRequestGroupCollectionResponse(groups, version, collection.updatedAt());
    }

    private void validateMembers(final String userId, final List<String> mergeRequestIds) {
        final var distinct = distinct(mergeRequestIds);
        if (distinct.size() < 2 || distinct.size() != mergeRequestIds.size()
                || userMergeRequestService.summaries(userId, distinct).size() != distinct.size()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("A group requires at least two distinct synchronized merge requests").build();
        }
    }

    private List<String> distinct(final List<String> ids) {
        return List.copyOf(new java.util.LinkedHashSet<>(ids));
    }
}
