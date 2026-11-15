/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

/**
 * Defines the namespace level for review feedback queries.
 *
 * <ul>
 *   <li>{@code GROUP} — feedback for projects under a top-level group (e.g. {@code acme})</li>
 *   <li>{@code SUBGROUP} — feedback for projects under a subgroup (e.g. {@code acme/payments})</li>
 *   <li>{@code PROJECT} — feedback for one project (e.g. {@code acme/payments/checkout-api})</li>
 * </ul>
 *
 * GROUP and SUBGROUP both match the target namespace and its nested namespaces. PROJECT matches
 * the exact namespace and project name parsed from the fully-qualified path.
 */
public enum TargetScope {
    @JsonProperty("group")
    GROUP("group"),
    @JsonProperty("subgroup")
    SUBGROUP("subgroup"),
    @JsonProperty("project")
    PROJECT("project");

    private static final Map<String, TargetScope> LOOKUP;

    static {
        final Map<String, TargetScope> lookup = new HashMap<>();
        for (TargetScope scope : values()) {
            lookup.put(scope.value.toLowerCase(Locale.ROOT), scope);
            lookup.put(scope.name().toLowerCase(Locale.ROOT), scope);
        }
        LOOKUP = Map.copyOf(lookup);
    }

    private final String value;

    TargetScope(final String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TargetScope fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        final var scope = LOOKUP.get(value.toLowerCase(Locale.ROOT));
        if (scope == null) {
            throw new IllegalArgumentException("Unknown target scope: " + value);
        }
        return scope;
    }
}
