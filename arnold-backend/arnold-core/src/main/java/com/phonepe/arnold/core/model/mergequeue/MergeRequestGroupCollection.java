package com.phonepe.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

/**
 * Legacy per-user collection of groups, superseded by per-group {@link MergeRequestGroup} records.
 * Retained only so the housekeeping migration can read existing data.
 */
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
