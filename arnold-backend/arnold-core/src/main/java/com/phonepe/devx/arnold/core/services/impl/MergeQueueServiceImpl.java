package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueue;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueueItem;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeQueueStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueItemResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;
import com.phonepe.devx.arnold.models.mergequeue.EnrichedMergeQueueItemResponse;
import com.phonepe.devx.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

@Singleton
public class MergeQueueServiceImpl implements MergeQueueService {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

    private final AerospikeMergeQueueStore mergeQueueStore;
    private final AerospikeMergeRequestGroupStore groupStore;
    private final UserMergeRequestService userMergeRequestService;

    @Inject
    public MergeQueueServiceImpl(final AerospikeMergeQueueStore mergeQueueStore,
                                 final AerospikeMergeRequestGroupStore groupStore,
                                 final UserMergeRequestService userMergeRequestService) {
        this.mergeQueueStore = mergeQueueStore;
        this.groupStore = groupStore;
        this.userMergeRequestService = userMergeRequestService;
    }

    public MergeQueueServiceImpl(final AerospikeMergeQueueStore mergeQueueStore) {
        this(mergeQueueStore, null, null);
    }

    @Override
    public MergeQueueResponse get(final String userId) {
        return mergeQueueStore.getWithVersion(userId)
                .map(queue -> toResponse(queue.value(), queue.version()))
                .orElseGet(() -> new MergeQueueResponse(java.util.List.of(), 0, 0));
    }

    @Override
    public MergeQueueResponse add(final String userId, final AddMergeQueueItemRequest request) {
        return update(userId, queue -> {
            if (queue.items().stream().anyMatch(item -> Objects.equals(
                    item.mergeRequestId(), request.getMergeRequestId()))) {
                return queue;
            }
            final var items = new ArrayList<>(queue.items());
            items.add(new MergeQueueItem(UUID.randomUUID().toString(), "MERGE_REQUEST",
                    request.getMergeRequestId(), request.getRole(), "MANUAL", false));
            return new MergeQueue(queue.id(), items, System.currentTimeMillis(), queue.priorityOrder());
        });
    }

    @Override
    public MergeQueueResponse remove(final String userId, final String itemId) {
        return update(userId, queue -> new MergeQueue(queue.id(), queue.items().stream()
                .filter(item -> !item.id().equals(itemId))
                .toList(), System.currentTimeMillis(), queue.priorityOrder()));
    }

    @Override
    public MergeQueueResponse reorder(final String userId, final ReorderMergeQueueRequest request) {
        final var current = mergeQueueStore.getWithVersion(userId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (current.version() != request.getVersion()) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        final var byId = new HashMap<String, MergeQueueItem>();
        current.value().items().forEach(item -> byId.put(item.id(), item));
        if (new HashSet<>(request.getOrderedItemIds()).size() != byId.size()
                || !byId.keySet().equals(new HashSet<>(request.getOrderedItemIds()))) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var reordered = request.getOrderedItemIds().stream().map(byId::get).toList();
        final var next = new MergeQueue(userId, reordered, System.currentTimeMillis(), current.value().priorityOrder());
        try {
            mergeQueueStore.checkAndUpdate(next, current.version());
            return toResponse(next, current.version() + 1);
        } catch (ConcurrentUpdateException conflict) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
    }

    @Override
    public EnrichedMergeQueueResponse reorderPriority(final String userId, final ReorderMergeQueueRequest request) {
        final var current = mergeQueueStore.getWithVersion(userId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        final var enriched = getEnriched(userId);
        if (current.version() != request.getVersion() || enriched.version() != current.version()) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        final var availableIds = enriched.items().stream()
                .flatMap(item -> item.group() == null ? java.util.stream.Stream.of(item.mergeRequest())
                        : item.group().members().stream())
                .filter(member -> "opened".equalsIgnoreCase(member.mergeRequest().state())
                        || "open".equalsIgnoreCase(member.mergeRequest().state()))
                .map(member -> member.mergeRequest().id()).collect(Collectors.toSet());
        final var requestedIds = request.getOrderedItemIds();
        if (requestedIds == null || requestedIds.size() != availableIds.size()
                || !availableIds.equals(new HashSet<>(requestedIds))) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var next = new MergeQueue(userId, current.value().items(), System.currentTimeMillis(), requestedIds);
        try {
            mergeQueueStore.checkAndUpdate(next, current.version());
        } catch (ConcurrentUpdateException conflict) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        return getEnriched(userId);
    }

    @Override
    public EnrichedMergeQueueResponse getEnriched(final String userId) {
        final var queue = mergeQueueStore.getWithVersion(userId);
        final var queueValue = queue.map(com.livetheoogway.crudstore.core.Versioned::value)
                .orElseGet(() -> new MergeQueue(userId, List.of(), 0));
        final var groupIds = queueValue.items().stream()
                .filter(item -> "GROUP".equals(item.targetType()))
                .map(MergeQueueItem::targetId).toList();
        final var groupsById = groupStore == null ? Map.<String, MergeRequestGroup>of()
                : groupStore.get(groupIds);
        final var mrIds = new LinkedHashSet<String>();
        queueValue.items().forEach(item -> {
            if ("GROUP".equals(item.targetType())) {
                final var group = groupsById.get(item.targetId());
                if (group != null) {
                    mrIds.addAll(group.mergeRequestIds());
                }
            } else {
                mrIds.add(item.targetId());
            }
        });
        final var summaries = userMergeRequestService == null ? Map
                .<String, com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestResponse>of()
                : userMergeRequestService.summaries(userId, mrIds);
        final var items = queueValue.items().stream().map(item -> {
            if ("GROUP".equals(item.targetType())) {
                final var group = groupsById.get(item.targetId());
                final var response = group == null ? null : new MergeRequestGroupResponse(group.id(), group.name(),
                        group.mergeRequestIds().stream().map(summaries::get).filter(java.util.Objects::nonNull).toList(),
                        group.createdAt(), group.updatedAt());
                final var blocked = response != null && response.members().stream()
                        .anyMatch(com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestResponse::blocked);
                return new EnrichedMergeQueueItemResponse(item.id(), "GROUP", item.targetId(), item.role(),
                        item.source(), blocked, null, response);
            }
            final var summary = summaries.get(item.targetId());
            return new EnrichedMergeQueueItemResponse(item.id(), "MERGE_REQUEST", item.targetId(), item.role(),
                    item.source(), summary != null && summary.blocked(), summary, null);
        }).filter(item -> item.mergeRequest() != null
                || item.group() != null && !item.group().members().isEmpty()).toList();
        // groupVersion is now the max updatedAt across the queue's groups; delete CAS checks it per group.
        final var groupVersion = groupsById.values().stream()
                .mapToLong(MergeRequestGroup::updatedAt).max().orElse(0L);
        return new EnrichedMergeQueueResponse(items, queueValue.updatedAt(),
                queue.map(com.livetheoogway.crudstore.core.Versioned::version).orElse(0L),
                groupVersion, queueValue.priorityOrder());
    }

    @Override
    public void reconcile(final String userId, final List<UserChangeRequest> associations) {
        if (groupStore == null) {
            return;
        }
        final var activeOpen = associations.stream().filter(UserChangeRequest::active)
                .filter(UserChangeRequest::open)
                .collect(Collectors.toMap(UserChangeRequest::mergeRequestId, value -> value, (left, right) -> right));
        final var groupIds = mergeQueueStore.getWithVersion(userId)
                .map(value -> value.value().items().stream()
                        .filter(item -> "GROUP".equals(item.targetType()))
                        .map(MergeQueueItem::targetId).toList())
                .orElseGet(List::of);
        final var groups = groupStore.get(groupIds);
        update(userId, queue -> {
            final var retained = new ArrayList<MergeQueueItem>();
            final var represented = new HashSet<String>();
            queue.items().forEach(item -> {
                if ("GROUP".equals(item.targetType())) {
                    final var group = groups.get(item.targetId());
                    if (group != null && group.mergeRequestIds().stream().anyMatch(activeOpen::containsKey)) {
                        retained.add(item);
                        represented.addAll(group.mergeRequestIds());
                    }
                } else if (activeOpen.containsKey(item.targetId())) {
                    retained.add(item);
                    represented.add(item.targetId());
                }
            });
            activeOpen.forEach((mrId, association) -> {
                if (!represented.contains(mrId)) {
                    final var role = queueRole(association);
                    retained.add(new MergeQueueItem(UUID.randomUUID().toString(), "MERGE_REQUEST", mrId,
                            role, "SYNC", false));
                }
            });
            return retained.equals(queue.items()) ? queue
                    : new MergeQueue(queue.id(), retained, System.currentTimeMillis(), queue.priorityOrder());
        });
    }

    @Override
    public void replaceMembersWithGroup(final String userId,
                                        final String groupId,
                                        final List<String> mergeRequestIds) {
        update(userId, queue -> {
            final var members = new HashSet<>(mergeRequestIds);
            final var retained = new ArrayList<MergeQueueItem>();
            var insertionIndex = queue.items().size();
            for (final var item : queue.items()) {
                if (!"GROUP".equals(item.targetType()) && members.contains(item.targetId())) {
                    insertionIndex = Math.min(insertionIndex, retained.size());
                } else if (!("GROUP".equals(item.targetType()) && groupId.equals(item.targetId()))) {
                    retained.add(item);
                }
            }
            final var groupItem = new MergeQueueItem(UUID.randomUUID().toString(), "GROUP", groupId,
                    "author", "MANUAL", false);
            retained.add(Math.min(insertionIndex, retained.size()), groupItem);
            return new MergeQueue(queue.id(), retained, System.currentTimeMillis(), queue.priorityOrder());
        });
    }

    @Override
    public void removeGroup(final String userId, final String groupId) {
        update(userId, queue -> new MergeQueue(queue.id(), queue.items().stream()
                .filter(item -> !("GROUP".equals(item.targetType()) && groupId.equals(item.targetId())))
                .toList(), System.currentTimeMillis(), queue.priorityOrder()));
    }

    private MergeQueueResponse update(final String queueId, final UnaryOperator<MergeQueue> mutation) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = mergeQueueStore.getWithVersion(queueId);
            if (current.isEmpty()) {
                final var queue = mutation.apply(new MergeQueue(queueId, java.util.List.of(), 0));
                try {
                    mergeQueueStore.create(queue);
                    return toResponse(queue, 0);
                } catch (RuntimeException createRace) {
                    continue;
                }
            }
            final var next = mutation.apply(current.get().value());
            if (next == current.get().value()) {
                return toResponse(next, current.get().version());
            }
            try {
                mergeQueueStore.checkAndUpdate(next, current.get().version());
                return toResponse(next, current.get().version() + 1);
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest ordering.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private MergeQueueResponse toResponse(final MergeQueue queue, final long version) {
        return new MergeQueueResponse(queue.items().stream()
                .map(item -> new MergeQueueItemResponse(item.id(), item.mergeRequestId(), item.role(), item.blocked()))
                .toList(), queue.updatedAt(), version);
    }

    private String queueRole(final UserChangeRequest association) {
        if (association.roles().contains(com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole.REVIEWER)) {
            return "reviewer";
        }
        if (association.roles().contains(com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole.ASSIGNEE)) {
            return "reviewer";
        }
        return "author";
    }

}
