package com.phonepe.devx.arnold.core.models.feedback;

/**
 * Defines the scope level for rule generation.
 *
 * <ul>
 *   <li>{@code GROUP} — rules apply to all projects under a top-level group (e.g. {@code devx})</li>
 *   <li>{@code SUBGROUP} — rules apply to all projects under a subgroup (e.g. {@code devx/payments})</li>
 *   <li>{@code PROJECT} — rules apply to a single project (e.g. {@code devx/payments/checkout-api})</li>
 * </ul>
 *
 * The scope determines:
 * <ol>
 *   <li>How feedback is queried from ES (prefix vs exact match)</li>
 *   <li>Where rule files are written in the skills repo</li>
 * </ol>
 */
public enum TargetScope {
    GROUP,
    SUBGROUP,
    PROJECT
}
