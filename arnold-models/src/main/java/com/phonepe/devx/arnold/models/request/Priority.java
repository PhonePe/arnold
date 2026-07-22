package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Priority {
    @JsonProperty("critical")
    CRITICAL("critical", 0),
    @JsonProperty("high")
    HIGH("high", 1),
    @JsonProperty("medium")
    MEDIUM("medium", 2),
    @JsonProperty("low")
    LOW("low", 3);

    private final String value;
    private final int rank;

    Priority(String value, int rank) {
        this.value = value;
        this.rank = rank;
    }

    public String getValue() {
        return value;
    }

    public int getRank() {
        return rank;
    }
}
