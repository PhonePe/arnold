package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AcceptanceStatus {
    @JsonProperty("applied")
    APPLIED,
    @JsonProperty("partially_applied")
    PARTIALLY_APPLIED,
    @JsonProperty("ignored")
    IGNORED,
    @JsonProperty("not_applicable")
    NOT_APPLICABLE;

    public String getValue() {
        return name().toLowerCase();
    }

    public static AcceptanceStatus fromString(final String value) {
        return AcceptanceStatus.valueOf(value.toUpperCase());
    }
}
