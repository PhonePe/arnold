package com.phonepe.devx.arnold.core.model.review;

/**
 * Lifecycle states of an Arnold change-request review. Enhanced over raw provider states to model
 * Arnold's analysis phases.
 *
 * <p>{@code MERGED} is the only terminal state. {@code CLOSED} is
 * <i>not</i> terminal because providers can reopen a closed change request, so the machine can leave
 * {@code CLOSED} via {@code CHANGE_REOPENED}.
 *
 * <p>Analysis failure is intentionally <b>not</b> a state here: a failed analysis still resolves to
 * {@code ANALYSED}, carrying the failure outcome in {@link ReviewStateData}. Retrying/alerting on a
 * failed run is handled outside the state machine.
 */
public enum ReviewState {
    CREATED,
    ANALYSING,
    ANALYSED,
    REVIEW_READY,
    MERGED,
    CLOSED
}
