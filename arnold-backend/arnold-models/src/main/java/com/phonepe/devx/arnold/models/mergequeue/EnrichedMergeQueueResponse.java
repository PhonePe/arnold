package com.phonepe.devx.arnold.models.mergequeue;

import java.util.List;

public record EnrichedMergeQueueResponse(
        List<EnrichedMergeQueueItemResponse> items,
        long updatedAt,
        long version,
        long groupVersion
) {
    public EnrichedMergeQueueResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
