/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
