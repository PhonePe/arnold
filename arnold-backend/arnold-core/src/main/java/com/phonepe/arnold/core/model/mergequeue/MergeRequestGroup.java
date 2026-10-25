package com.phonepe.arnold.core.model.mergequeue;

import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record MergeRequestGroup(
        String id,
        String createdBy,
        String name,
        List<String> mergeRequestIds,
        long createdAt,
        long updatedAt
) implements Id {
    public MergeRequestGroup {
        mergeRequestIds = mergeRequestIds == null ? List.of() : List.copyOf(mergeRequestIds);
    }
}
