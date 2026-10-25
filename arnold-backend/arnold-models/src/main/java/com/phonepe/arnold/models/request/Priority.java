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

public enum Priority {
    @JsonProperty("critical")
    CRITICAL("critical", 0),
    @JsonProperty("high")
    HIGH("high", 1),
    @JsonProperty("medium")
    MEDIUM("medium", 2),
    @JsonProperty("low")
    LOW("low", 3);

    private static final Map<String, Priority> LOOKUP;

    static {
        final Map<String, Priority> lookup = new HashMap<>();
        for (Priority priority : values()) {
            lookup.put(priority.value.toLowerCase(Locale.ROOT), priority);
            lookup.put(priority.name().toLowerCase(Locale.ROOT), priority);
        }
        LOOKUP = Map.copyOf(lookup);
    }

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
        final var priority = LOOKUP.get(value.toLowerCase(Locale.ROOT));
        if (priority == null) {
            throw new IllegalArgumentException("Unknown priority: " + value);
        }
        return priority;
    }
}
