package com.phonepe.devx.arnold.core.review.analysis;

/**
 * Outcome of one persona's CIBot review within an analysis run, as delivered by the CIBot completion
 * webhook. {@code runId} fences the result against the MR's current in-flight run so callbacks from a
 * superseded re-run can be dropped. The orchestrator aggregates these per-persona outcomes into the
 * run-level {@link com.phonepe.devx.arnold.core.review.AnalysisOutcome} it reports to the machine.
 */
public record PersonaReviewResult(
        String runId,
        String personaId,
        Outcome outcome,
        String detail
) {
    public enum Outcome {
        SUCCEEDED,
        FAILED
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCEEDED;
    }
}
