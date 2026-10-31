package com.phonepe.devx.arnold.models.mergequeue;

import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestResponse;

public record EnrichedMergeQueueItemResponse(
        String id,
        String targetType,
        String targetId,
        String role,
        String source,
        boolean blocked,
        UserMergeRequestResponse mergeRequest,
        MergeRequestGroupResponse group
) {
}
