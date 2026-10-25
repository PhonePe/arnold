package com.phonepe.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum CommentType {
    @JsonProperty("inline")
    INLINE("inline"),
    @JsonProperty("general")
    GENERAL("general");

    private static final Map<String, CommentType> LOOKUP;

    static {
        final Map<String, CommentType> lookup = new HashMap<>();
        for (CommentType commentType : values()) {
            lookup.put(commentType.value.toLowerCase(Locale.ROOT), commentType);
            lookup.put(commentType.name().toLowerCase(Locale.ROOT), commentType);
        }
        LOOKUP = Map.copyOf(lookup);
    }

    private final String value;

    CommentType(final String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CommentType fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        final var commentType = LOOKUP.get(value.toLowerCase(Locale.ROOT));
        if (commentType == null) {
            throw new IllegalArgumentException("Unknown comment type: " + value);
        }
        return commentType;
    }
}
