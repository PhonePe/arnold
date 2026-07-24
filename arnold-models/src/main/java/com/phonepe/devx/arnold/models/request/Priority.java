package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

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

    Priority(final String value, final int rank) {
        this.value = value;
        this.rank = rank;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public int getRank() {
        return rank;
    }

    @JsonCreator
    public static Priority fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return Arrays.stream(values())
                .filter(priority -> priority.value.equalsIgnoreCase(value)
                        || priority.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown priority: " + value));
    }
}
