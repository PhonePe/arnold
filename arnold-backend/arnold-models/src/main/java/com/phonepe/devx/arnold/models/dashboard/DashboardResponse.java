package com.phonepe.devx.arnold.models.dashboard;

import com.phonepe.devx.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.devx.arnold.models.integration.VersionControlIntegrationResponse;
import com.phonepe.devx.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestResponse;
import com.phonepe.devx.arnold.models.notification.NotificationPageResponse;
import com.phonepe.devx.arnold.models.stats.StatsResponse;
import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;
import com.phonepe.devx.arnold.models.user.UserResponse;

import java.util.List;

public record DashboardResponse(
        UserResponse user,
        VersionControlIntegrationResponse integration,
        CopilotConnectionResponse copilot,
        UserSyncStatusResponse sync,
        EnrichedMergeQueueResponse queue,
        List<UserMergeRequestResponse> openMergeRequests,
        StatsResponse productivity,
        NotificationPageResponse notifications
) {
    public DashboardResponse {
        openMergeRequests = openMergeRequests == null ? List.of() : List.copyOf(openMergeRequests);
    }
}
