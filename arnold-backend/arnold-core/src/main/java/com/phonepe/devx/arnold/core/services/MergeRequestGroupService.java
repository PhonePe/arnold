package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupResponse;
import com.phonepe.devx.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;

public interface MergeRequestGroupService {
    MergeRequestGroupResponse get(String groupId);

    MergeRequestGroupCollectionResponse list(String userId);

    MergeRequestGroupCollectionResponse create(String userId, CreateMergeRequestGroupRequest request);

    MergeRequestGroupResponse update(String userId, String groupId, UpdateMergeRequestGroupRequest request);

    void delete(String userId, String groupId, long version);
}
