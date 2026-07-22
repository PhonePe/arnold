package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Priority {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("high")
    HIGH,
    @JsonProperty("medium")
    MEDIUM,
    @JsonProperty("low")
    LOW
}
