package com.phonepe.devx.arnold.core.model.mergequeue;

import java.util.List;

public record MergeRequestGroup(
        String id,
        String name,
        List<String> mergeRequestIds,
        long createdAt,
        long updatedAt
) {
    public MergeRequestGroup {
        mergeRequestIds = mergeRequestIds == null ? List.of() : List.copyOf(mergeRequestIds);
    }
}
