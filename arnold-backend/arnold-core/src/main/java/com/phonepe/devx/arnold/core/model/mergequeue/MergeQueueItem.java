package com.phonepe.devx.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MergeQueueItem(
        String id,
        String targetType,
        String targetId,
        String role,
        String source,
        boolean blocked
) {
    @JsonCreator
    public MergeQueueItem(@JsonProperty("id") final String id,
                          @JsonProperty("targetType") final String targetType,
                          @JsonProperty("targetId") @JsonAlias("mergeRequestId") final String targetId,
                          @JsonProperty("role") final String role,
                          @JsonProperty("source") final String source,
                          @JsonProperty("blocked") final boolean blocked) {
        this.id = id;
        this.targetType = targetType == null ? "MERGE_REQUEST" : targetType;
        this.targetId = targetId;
        this.role = role;
        this.source = source == null ? "MANUAL" : source;
        this.blocked = blocked;
    }

    public MergeQueueItem(final String id, final String mergeRequestId, final String role, final boolean blocked) {
        this(id, "MERGE_REQUEST", mergeRequestId, role, "MANUAL", blocked);
    }

    public String mergeRequestId() {
        return targetId;
    }
}
