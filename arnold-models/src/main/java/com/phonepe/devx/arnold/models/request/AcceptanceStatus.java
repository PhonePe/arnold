package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    AcceptanceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
