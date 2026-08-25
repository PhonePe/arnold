package com.phonepe.devx.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record MergeRequestGroupCollection(
        String id,
        List<MergeRequestGroup> groups,
        long updatedAt
) implements Id {
    @JsonCreator
    public MergeRequestGroupCollection(@JsonProperty("id") final String id,
                                       @JsonProperty("groups") final List<MergeRequestGroup> groups,
                                       @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.updatedAt = updatedAt;
    }
}
