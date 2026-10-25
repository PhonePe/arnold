package com.phonepe.arnold.models.mergequeue;

import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;

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
