package com.phonepe.devx.arnold.core.models.feedback;

/**
 * Defines the namespace level for review feedback queries.
 *
 * <ul>
 *   <li>{@code GROUP} — feedback for projects under a top-level group (e.g. {@code devx})</li>
 *   <li>{@code SUBGROUP} — feedback for projects under a subgroup (e.g. {@code devx/payments})</li>
 *   <li>{@code PROJECT} — feedback for one project (e.g. {@code devx/payments/checkout-api})</li>
 * </ul>
 *
 * GROUP and SUBGROUP both match the target namespace and its nested namespaces. PROJECT matches
 * the exact namespace and project name parsed from the fully-qualified path.
 */
public enum TargetScope {
    GROUP,
    SUBGROUP,
    PROJECT
}
