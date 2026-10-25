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
