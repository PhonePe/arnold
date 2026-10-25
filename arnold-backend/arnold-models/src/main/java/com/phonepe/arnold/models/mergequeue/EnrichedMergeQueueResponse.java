package com.phonepe.arnold.models.mergequeue;

import java.util.List;

public record EnrichedMergeQueueResponse(
        List<EnrichedMergeQueueItemResponse> items,
        long updatedAt,
        long version,
        long groupVersion,
        List<String> priorityOrder
) {
    public EnrichedMergeQueueResponse {
        items = items == null ? List.of() : List.copyOf(items);
        priorityOrder = priorityOrder == null ? List.of() : List.copyOf(priorityOrder);
    }
}
