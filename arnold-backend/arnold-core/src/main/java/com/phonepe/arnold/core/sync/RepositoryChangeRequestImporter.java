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
package com.phonepe.arnold.core.sync;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.RepositorySyncProvider;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists change requests discovered by repository-scoped syncs and GitHub webhooks through the same path as
 * user syncs, and links them to Arnold users whose provider identity is the author, an assignee or a reviewer.
 */
@Slf4j
@Singleton
public class RepositoryChangeRequestImporter {
    private final ChangeRequestService changeRequests;
    private final VersionControlSyncService syncService;
    private final UserMergeRequestService userMergeRequests;
    private final ProviderIdentityService identities;
    private final MergeQueueService mergeQueue;

    @Inject
    public RepositoryChangeRequestImporter(final ChangeRequestService changeRequests,
                                           final VersionControlSyncService syncService,
                                           final UserMergeRequestService userMergeRequests,
                                           final ProviderIdentityService identities,
                                           final MergeQueueService mergeQueue) {
        this.changeRequests = changeRequests;
        this.syncService = syncService;
        this.userMergeRequests = userMergeRequests;
        this.identities = identities;
        this.mergeQueue = mergeQueue;
    }

    /**
     * Imports the open change requests of {@code repositoryIds}. When {@code complete}, stored change requests of those
     * repositories that are still open locally but missing from {@code open} are refreshed individually (at most
     * {@code maxReconcile}) so merges and closes are recorded.
     *
     * @param refresh fetches the current provider state; empty when the change request no longer exists
     * @return the number of change requests in {@code open} that were persisted
     */
    public int importOpen(final VersionControlProviderType provider,
                          final Collection<String> repositoryIds,
                          final List<ChangeRequestRecord> open,
                          final boolean complete,
                          final int maxReconcile,
                          final Function<ChangeRequestRecord, Optional<ChangeRequestRecord>> refresh) {
        final var affectedUsers = new HashSet<String>();
        var imported = 0;
        for (final var changeRequest : open) {
            try {
                if (upsert(changeRequest, affectedUsers)) {
                    imported++;
                }
            } catch (RuntimeException failure) {
                log.warn("Could not import {} change request {}", provider, changeRequest.id(), failure);
            }
        }
        if (complete) {
            final var openIds = open.stream().map(ChangeRequestRecord::id).collect(Collectors.toSet());
            stale(provider, Set.copyOf(repositoryIds), openIds, maxReconcile).forEach(stored -> {
                try {
                    refresh.apply(stored).ifPresent(current -> upsert(current, affectedUsers));
                } catch (ArnoldError error) {
                    if (RepositorySyncProvider.rateLimited(error)) {
                        throw error;
                    }
                    log.warn("Could not refresh {} change request {}", provider, stored.id(), error);
                }
            });
        }
        reconcileQueues(affectedUsers);
        return imported;
    }

    /**
     * Persists one change request and refreshes user associations.
     *
     * @param affectedUsers receives the Arnold users whose association with the change request was written
     * @return false when the stored change request is newer and nothing was written
     */
    public boolean upsert(final ChangeRequestRecord current, final Set<String> affectedUsers) {
        final var previous = changeRequests.record(current.id()).orElse(null);
        final var record = previous == null ? current : current.withProjectArchived(previous.projectArchived());
        if (!syncService.synchronize(new ChangeRequestSync(record, ChangeRequestSync.actionFor(previous, record),
                null))) {
            return false;
        }
        affectedUsers.addAll(associate(record));
        return true;
    }

    public void reconcileQueues(final Set<String> userIds) {
        userIds.forEach(userId -> {
            try {
                mergeQueue.reconcile(userId, userMergeRequests.associationsForUser(userId));
            } catch (RuntimeException failure) {
                log.warn("Could not reconcile merge queue for user {}", userId, failure);
            }
        });
    }

    private Set<String> associate(final ChangeRequestRecord changeRequest) {
        final var roles = new HashMap<String, Set<MergeRequestRole>>();
        addRole(roles, changeRequest.provider(), changeRequest.authorId(), MergeRequestRole.AUTHOR);
        changeRequest.assigneeIds().forEach(id -> addRole(roles, changeRequest.provider(), id,
                MergeRequestRole.ASSIGNEE));
        changeRequest.reviewerIds().forEach(id -> addRole(roles, changeRequest.provider(), id,
                MergeRequestRole.REVIEWER));
        final var seenAt = System.currentTimeMillis();
        roles.forEach((userId, userRoles) -> userMergeRequests.upsert(userId, changeRequest, userRoles, seenAt));
        // Associations written by user syncs for users no longer listed keep their roles; only the open flag moves.
        userMergeRequests.associationsForMergeRequest(changeRequest.id()).stream()
                .filter(association -> !roles.containsKey(association.userId()))
                .filter(association -> association.open() != changeRequest.isOpen())
                .forEach(association -> {
                    userMergeRequests.upsert(association.userId(), changeRequest, association.roles(), seenAt);
                    roles.put(association.userId(), association.roles());
                });
        return roles.keySet();
    }

    private void addRole(final Map<String, Set<MergeRequestRole>> roles, final VersionControlProviderType provider,
                         final Long providerUserId, final MergeRequestRole role) {
        if (providerUserId == null || providerUserId <= 0) {
            return;
        }
        identities.findUserId(provider, Long.toString(providerUserId)).ifPresent(userId ->
                roles.computeIfAbsent(userId, ignored -> EnumSet.noneOf(MergeRequestRole.class)).add(role));
    }

    private List<ChangeRequestRecord> stale(final VersionControlProviderType provider, final Set<String> repositoryIds,
                                            final Set<String> openIds, final int maxReconcile) {
        if (repositoryIds.isEmpty()) {
            return List.of();
        }
        return changeRequests.list().stream()
                .filter(record -> record.provider() == provider)
                .filter(ChangeRequestRecord::isOpen)
                .filter(record -> repositoryIds.contains(record.repositoryId()))
                .filter(record -> !openIds.contains(record.id()))
                .sorted(Comparator.comparingLong(ChangeRequestRecord::lastSyncedAt))
                .limit(maxReconcile)
                .toList();
    }
}
