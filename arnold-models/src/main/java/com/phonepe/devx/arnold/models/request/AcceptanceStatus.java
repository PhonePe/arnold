package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum AcceptanceStatus {
    @JsonProperty("applied")
    APPLIED("applied"),
    @JsonProperty("partially_applied")
    PARTIALLY_APPLIED("partially_applied"),
    @JsonProperty("ignored")
    IGNORED("ignored"),
    @JsonProperty("not_applicable")
    NOT_APPLICABLE("not_applicable");

    private final String value;

    AcceptanceStatus(final String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AcceptanceStatus fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value)
                        || status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown acceptance status: " + value));
    }
}
