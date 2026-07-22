package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CommentType {
    @JsonProperty("inline")
    INLINE,
    @JsonProperty("general")
    GENERAL
}
