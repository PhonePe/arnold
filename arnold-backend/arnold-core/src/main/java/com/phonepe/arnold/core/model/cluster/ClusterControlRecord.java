package com.phonepe.arnold.core.model.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

public record ClusterControlRecord(
        String id,
        String kind,
        String value,
        String ownerId,
        long leaseUntil,
        long updatedAt
) implements Id {
    @JsonCreator
    public ClusterControlRecord(@JsonProperty("id") final String id,
                                @JsonProperty("kind") final String kind,
                                @JsonProperty("value") final String value,
                                @JsonProperty("ownerId") final String ownerId,
                                @JsonProperty("leaseUntil") final long leaseUntil,
                                @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.kind = kind;
        this.value = value;
        this.ownerId = ownerId;
        this.leaseUntil = leaseUntil;
        this.updatedAt = updatedAt;
    }

    public static String settingId(final String name) {
        return "setting:" + name;
    }

    public static String leaderId(final String name) {
        return "leader:" + name;
    }
}
