/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.ChangeRequestNotificationPublisher;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserMergeRequestService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class GitLabWebhookFanoutServiceImpl implements GitLabWebhookFanoutService {
    private final ProviderIdentityService providerIdentityService;
    private final UserMergeRequestService mergeRequestService;
    private final MergeQueueService mergeQueueService;
    private final ChangeRequestNotificationPublisher notificationPublisher;

    @Inject
    public GitLabWebhookFanoutServiceImpl(final ProviderIdentityService providerIdentityService,
                                          final UserMergeRequestService mergeRequestService,
                                          final MergeQueueService mergeQueueService,
                                          final ChangeRequestNotificationPublisher notificationPublisher) {
        this.providerIdentityService = providerIdentityService;
        this.mergeRequestService = mergeRequestService;
        this.mergeQueueService = mergeQueueService;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public void synchronize(final ChangeRequestRecord previous, final ChangeRequestRecord current) {
        final var existing = mergeRequestService.associationsForMergeRequest(current.id());
        final var previousByUser = new HashMap<String, UserChangeRequest>();
        existing.forEach(association -> previousByUser.put(association.userId(), association));

        final var rolesByUser = new HashMap<String, EnumSet<MergeRequestRole>>();
        addRole(rolesByUser, current.authorId(), MergeRequestRole.AUTHOR);
        current.assigneeIds().forEach(providerUserId ->
                addRole(rolesByUser, providerUserId, MergeRequestRole.ASSIGNEE));
        current.reviewerIds().forEach(providerUserId ->
                addRole(rolesByUser, providerUserId, MergeRequestRole.REVIEWER));

        final var affectedUsers = new HashSet<>(previousByUser.keySet());
        affectedUsers.addAll(rolesByUser.keySet());
        final var seenAt = System.currentTimeMillis();
        affectedUsers.forEach(userId -> {
            final var previousAssociation = previousByUser.get(userId);
            final var roles = rolesByUser.getOrDefault(userId, EnumSet.noneOf(MergeRequestRole.class));
            final Set<MergeRequestRole> persistedRoles = !isOpen(current) && roles.isEmpty()
                    && previousAssociation != null ? previousAssociation.roles() : roles;
            final var next = mergeRequestService.upsert(userId, current, persistedRoles, seenAt);
            notificationPublisher.rolesChanged(current, previousAssociation, next);
            mergeQueueService.reconcile(userId, mergeRequestService.associationsForUser(userId));
        });

        notificationPublisher.stateChanged(previous, current,
                mergeRequestService.associationsForMergeRequest(current.id()));
    }

    @Override
    public void reviewRequested(final ChangeRequestRecord current, final Set<Long> reviewerIds) {
        if (!current.isOpen() || current.projectArchived()) {
            return;
        }
        final var recipients = new HashSet<String>();
        reviewerIds.stream().filter(current.reviewerIds()::contains).forEach(id ->
                providerIdentityService.findUserId(current.provider(), Long.toString(id)).ifPresent(recipients::add));
        notificationPublisher.reviewRequested(current, recipients);
    }

    private void addRole(final Map<String, EnumSet<MergeRequestRole>> rolesByUser,
                         final Long providerUserId,
                         final MergeRequestRole role) {
        if (providerUserId == null) {
            return;
        }
        providerIdentityService.findUserId(VersionControlProviderType.GITLAB, Long.toString(providerUserId))
                .ifPresent(userId -> rolesByUser.computeIfAbsent(userId,
                        ignored -> EnumSet.noneOf(MergeRequestRole.class)).add(role));
    }

    private boolean isOpen(final ChangeRequestRecord changeRequest) {
        return "opened".equalsIgnoreCase(changeRequest.state()) || "open".equalsIgnoreCase(changeRequest.state());
    }

}
