package com.phonepe.arnold.models.mergequeue;

import java.util.List;

public record MergeQueueResponse(List<MergeQueueItemResponse> items, long updatedAt, long version) {
}
