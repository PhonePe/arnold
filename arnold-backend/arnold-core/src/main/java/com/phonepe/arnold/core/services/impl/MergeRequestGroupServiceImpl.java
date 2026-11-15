/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.MergeRequestGroupService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.arnold.models.mergequeue.MergeRequestGroupResponse;
import com.phonepe.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
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
    public MergeRequestGroupResponse get(final String groupId) {
        return response(group(groupId));
    }

    @Override
    public MergeRequestGroupCollectionResponse list(final String userId) {
        // ponytail: created-by filter scans all groups; add a creator index when group volume requires it.
        final var groups = store.list().stream()
                .filter(group -> userId.equals(group.createdBy()))
                .sorted(Comparator.comparingLong(MergeRequestGroup::createdAt).reversed())
                .toList();
        return new MergeRequestGroupCollectionResponse(groups.stream().map(this::response).toList(), 0, 0);
    }

    @Override
    public MergeRequestGroupCollectionResponse create(final String userId,
                                                      final CreateMergeRequestGroupRequest request) {
        final var members = validateMembers(userId, request.getMergeRequestIds());
        final var group = new MergeRequestGroup(UUID.randomUUID().toString(), userId, request.getName().trim(),
                members, System.currentTimeMillis(), System.currentTimeMillis());
        try {
            store.create(group);
        } catch (RuntimeException race) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        mergeQueueService.replaceMembersWithGroup(userId, group.id(), members);
        return list(userId);
    }

    @Override
    public MergeRequestGroupResponse update(final String userId,
                                            final String groupId,
                                            final UpdateMergeRequestGroupRequest request) {
        final var updated = mutate(userId, groupId, group -> new MergeRequestGroup(group.id(), group.createdBy(),
                request.getName().trim(), validateMembers(userId, request.getMergeRequestIds()),
                group.createdAt(), System.currentTimeMillis()));
        mergeQueueService.replaceMembersWithGroup(userId, groupId, updated.mergeRequestIds());
        return response(updated);
    }

    @Override
    public void delete(final String userId, final String groupId, final long version) {
        owned(userId, groupId);
        try {
            store.delete(groupId);
        } catch (RuntimeException missing) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        mergeQueueService.removeGroup(userId, groupId);
        mergeQueueService.reconcile(userId, userMergeRequestService.associationsForUser(userId));
    }

    private MergeRequestGroup mutate(final String userId, final String groupId,
                                     final UnaryOperator<MergeRequestGroup> mutation) {
        for (var attempt = 0; attempt < 8; attempt++) {
            final var current = owned(userId, groupId);
            final var next = mutation.apply(current);
            if (next.equals(current)) return next;
            try {
                store.checkAndUpdate(next, store.getWithVersion(groupId)
                        .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build()).version());
                return next;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest revision.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private MergeRequestGroup group(final String groupId) {
        return store.get(groupId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    private MergeRequestGroup owned(final String userId, final String groupId) {
        final var group = group(groupId);
        if (!userId.equals(group.createdBy())) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        return group;
    }

    private MergeRequestGroupResponse response(final MergeRequestGroup group) {
        final var summaries = userMergeRequestService.summaries(group.createdBy(), group.mergeRequestIds());
        return new MergeRequestGroupResponse(group.id(), group.name(),
                group.mergeRequestIds().stream().map(summaries::get)
                        .filter(java.util.Objects::nonNull).toList(),
                group.createdAt(), group.updatedAt());
    }

    private List<String> validateMembers(final String userId, final List<String> mergeRequestIds) {
        final var distinct = mergeRequestIds == null ? List.<String>of()
                : List.copyOf(new java.util.LinkedHashSet<>(mergeRequestIds));
        if (distinct.size() < 2 || distinct.size() != (mergeRequestIds == null ? 0 : mergeRequestIds.size())
                || userMergeRequestService.summaries(userId, distinct).size() != distinct.size()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("A group requires at least two distinct synchronized merge requests").build();
        }
        return distinct;
    }
}
