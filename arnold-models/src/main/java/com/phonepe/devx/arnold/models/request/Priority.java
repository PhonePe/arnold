package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Priority {
    @JsonProperty("critical")
    CRITICAL(0),
    @JsonProperty("high")
    HIGH(1),
    @JsonProperty("medium")
    MEDIUM(2),
    @JsonProperty("low")
    LOW(3);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public String getValue() {
        return name().toLowerCase();
    }

    public int getRank() {
        return rank;
    }

    public static Priority fromString(final String value) {
        return Priority.valueOf(value.toUpperCase());
    }
}
