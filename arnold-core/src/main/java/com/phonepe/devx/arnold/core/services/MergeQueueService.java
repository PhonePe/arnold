package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;

public interface MergeQueueService {
    MergeQueueResponse get(String userId);

    MergeQueueResponse add(String userId, AddMergeQueueItemRequest request);

    MergeQueueResponse remove(String userId, String itemId);

    MergeQueueResponse reorder(String userId, ReorderMergeQueueRequest request);
}
