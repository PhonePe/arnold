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
