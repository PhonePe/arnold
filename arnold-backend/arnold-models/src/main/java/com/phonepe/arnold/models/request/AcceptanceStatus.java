package com.phonepe.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum AcceptanceStatus {
    @JsonProperty("applied")
    APPLIED("applied"),
    @JsonProperty("partially_applied")
    PARTIALLY_APPLIED("partially_applied"),
    @JsonProperty("ignored")
    IGNORED("ignored"),
    @JsonProperty("not_applicable")
    NOT_APPLICABLE("not_applicable");

    private static final Map<String, AcceptanceStatus> LOOKUP;

    static {
        final Map<String, AcceptanceStatus> lookup = new HashMap<>();
        for (AcceptanceStatus status : values()) {
            lookup.put(status.value.toLowerCase(Locale.ROOT), status);
            lookup.put(status.name().toLowerCase(Locale.ROOT), status);
        }
        LOOKUP = Map.copyOf(lookup);
    }

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
        final var status = LOOKUP.get(value.toLowerCase(Locale.ROOT));
        if (status == null) {
            throw new IllegalArgumentException("Unknown acceptance status: " + value);
        }
        return status;
    }
}
