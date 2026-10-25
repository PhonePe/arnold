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

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.DashboardService;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.NotificationService;
import com.phonepe.arnold.core.services.StatsService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.services.UserSyncService;
import com.phonepe.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.dashboard.DashboardResponse;
import com.phonepe.arnold.models.stats.StatsResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
@Slf4j
public class DashboardServiceImpl implements DashboardService {
    private final UserService userService;
    private final VersionControlIntegrationService integrationService;
    private final UserSyncService syncService;
    private final MergeQueueService mergeQueueService;
    private final UserMergeRequestService mergeRequestService;
    private final StatsService statsService;
    private final NotificationService notificationService;
    private final CopilotConnectionService copilotConnectionService;

    @Inject
    public DashboardServiceImpl(final UserService userService,
                                final VersionControlIntegrationService integrationService,
                                final UserSyncService syncService,
                                final MergeQueueService mergeQueueService,
                                final UserMergeRequestService mergeRequestService,
                                final StatsService statsService,
                                final NotificationService notificationService,
                                final CopilotConnectionService copilotConnectionService) {
        this.userService = userService;
        this.integrationService = integrationService;
        this.syncService = syncService;
        this.mergeQueueService = mergeQueueService;
        this.mergeRequestService = mergeRequestService;
        this.statsService = statsService;
        this.notificationService = notificationService;
        this.copilotConnectionService = copilotConnectionService;
    }

    @Override
    public DashboardResponse get(final ArnoldPrincipal principal) {
        final var user = userService.getOrCreateCurrentUser(principal);
        final var integration = integrationService.status(VersionControlProviderType.GITLAB, user.id());
        final var sync = integration.connected()
                ? syncService.ensureScheduled(VersionControlProviderType.GITLAB, user.id())
                : syncService.status(VersionControlProviderType.GITLAB, user.id());
        final var queue = mergeQueueService.getEnriched(user.id());
        final var open = mergeRequestService.list(user.id(), "author", 5, null).items();
        final StatsResponse stats;
        if (integration.connected() && sync.lastSucceededAt() > 0) {
            StatsResponse loaded;
            try {
                loaded = statsService.personal(user.id());
            } catch (RuntimeException unavailable) {
                log.warn("Failed to load dashboard stats for userId={}", user.id(), unavailable);
                loaded = emptyStats();
            }
            stats = loaded;
        } else {
            stats = emptyStats();
        }
        return new DashboardResponse(user, integration, copilotConnectionService.status(user.id()), sync, queue,
                open, stats, notificationService.list(user.id(), 10, null));
    }

    private StatsResponse emptyStats() {
        return new StatsResponse("personal", 0, 0, 0, 0, null, List.of(),
                System.currentTimeMillis(), 0);
    }
}
