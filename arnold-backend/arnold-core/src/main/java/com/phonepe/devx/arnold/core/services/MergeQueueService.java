package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;
import com.phonepe.devx.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;

import java.util.List;

public interface MergeQueueService {
    MergeQueueResponse get(String userId);

    MergeQueueResponse add(String userId, AddMergeQueueItemRequest request);

    MergeQueueResponse remove(String userId, String itemId);

    MergeQueueResponse reorder(String userId, ReorderMergeQueueRequest request);

    EnrichedMergeQueueResponse reorderPriority(String userId, ReorderMergeQueueRequest request);

    EnrichedMergeQueueResponse getEnriched(String userId);

    void reconcile(String userId, List<UserChangeRequest> associations);

    void replaceMembersWithGroup(String userId, String groupId, List<String> mergeRequestIds);

    void removeGroup(String userId, String groupId);
}
