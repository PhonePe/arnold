package com.phonepe.devx.arnold.core.review;

/**
 * Lifecycle states of an Arnold merge-request review. Enhanced over the raw GitLab MR states to model
 * Arnold's analysis phases.
 *
 * <p>{@code MERGED} is the only terminal state — GitLab cannot un-merge an MR. {@code CLOSED} is
 * <i>not</i> terminal: GitLab allows a closed MR to be reopened, so the machine must be able to leave
 * {@code CLOSED} via {@code GITLAB_REOPENED}.
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
