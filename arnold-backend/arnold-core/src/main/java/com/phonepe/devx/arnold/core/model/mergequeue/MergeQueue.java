package com.phonepe.devx.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record MergeQueue(String id, List<MergeQueueItem> items, long updatedAt) implements Id {

    @JsonCreator
    public MergeQueue(@JsonProperty("id") final String id,
                      @JsonProperty("items") final List<MergeQueueItem> items,
                      @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.updatedAt = updatedAt;
    }
}
