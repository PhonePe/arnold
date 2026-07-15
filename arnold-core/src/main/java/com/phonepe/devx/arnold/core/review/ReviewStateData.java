package com.phonepe.devx.arnold.core.review;

import lombok.Builder;
import lombok.With;

import java.time.Instant;

/**
 * Per-state data ({@code D}) carried in the review machine's {@link com.phonepe.devx.arnold.core.statemachine.StateInfo}.
 * Lightweight on purpose: the heavy analysis bookkeeping (per-persona progress) lives in the
 * orchestrator's own {@code AnalysisRun}; here we keep only what downstream states need.
 *
 * <ul>
 *   <li>{@code analysisRunId} / {@code personaCount} — identify the run that produced the current
 *       analysis.</li>
 *   <li>{@code analysisOutcome} / {@code outcomeDetail} — how the last analysis resolved (success,
 *       partial, or failure). Present from {@code ANALYSED} onward; the machine treats all outcomes as
 *       a successful arrival at {@code ANALYSED}.</li>
 *   <li>{@code preCloseState} — the state the MR was in immediately before GitLab closed it, captured
 *       so a later {@code GITLAB_REOPENED} can restore meaningful context instead of dropping the MR to
 *       {@code CREATED} unconditionally.</li>
 * </ul>
 */
@Builder(toBuilder = true)
@With
public record ReviewStateData(
        String analysisRunId,
        int personaCount,
        AnalysisOutcome analysisOutcome,
        String outcomeDetail,
        ReviewState preCloseState,
        Instant updatedAt
) {
    public static ReviewStateData initial() {
        return ReviewStateData.builder().updatedAt(Instant.now()).build();
    }
}
