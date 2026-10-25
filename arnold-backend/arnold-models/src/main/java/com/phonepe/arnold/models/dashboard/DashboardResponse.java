package com.phonepe.arnold.models.dashboard;

import com.phonepe.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;
import com.phonepe.arnold.models.notification.NotificationPageResponse;
import com.phonepe.arnold.models.stats.StatsResponse;
import com.phonepe.arnold.models.sync.UserSyncStatusResponse;
import com.phonepe.arnold.models.user.UserResponse;

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
