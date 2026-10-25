package com.phonepe.arnold.models.mergequeue;

import java.util.List;

public record MergeRequestGroupCollectionResponse(
        List<MergeRequestGroupResponse> groups,
        long version,
        long updatedAt
) {
    public MergeRequestGroupCollectionResponse {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
