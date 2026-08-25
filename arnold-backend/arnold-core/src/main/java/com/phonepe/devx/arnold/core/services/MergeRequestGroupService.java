package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.devx.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;

public interface MergeRequestGroupService {
    MergeRequestGroupCollectionResponse get(String userId);

    MergeRequestGroupCollectionResponse create(String userId, CreateMergeRequestGroupRequest request);

    MergeRequestGroupCollectionResponse update(String userId, String groupId, UpdateMergeRequestGroupRequest request);

    MergeRequestGroupCollectionResponse delete(String userId, String groupId, long version);
}
