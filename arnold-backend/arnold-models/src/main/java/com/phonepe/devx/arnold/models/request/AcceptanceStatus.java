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
    NOT_APPLICABLE
}
