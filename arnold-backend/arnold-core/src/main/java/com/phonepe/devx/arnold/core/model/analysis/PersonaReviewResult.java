package com.phonepe.devx.arnold.core.model.analysis;

import com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;

import java.util.List;

/**
 * Outcome of one persona's CIBot review within an analysis run, as delivered by the CIBot completion
 * webhook. {@code runId} fences the result against the MR's current in-flight run so callbacks from a
 * superseded re-run can be dropped. The orchestrator aggregates these per-persona outcomes into the
 * run-level {@link com.phonepe.devx.arnold.core.model.review.AnalysisOutcome} it reports to the machine.
 */
public record PersonaReviewResult(
        String runId,
        String personaId,
        Outcome outcome,
        String detail,
        List<AnalysisFindingResponse> findings,
        TokenUsage usage
) {
    public PersonaReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public PersonaReviewResult(final String runId, final String personaId, final Outcome outcome, final String detail,
                               final List<AnalysisFindingResponse> findings) {
        this(runId, personaId, outcome, detail, findings, null);
    }

    public PersonaReviewResult(final String runId, final String personaId, final Outcome outcome, final String detail) {
        this(runId, personaId, outcome, detail, List.of());
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCEEDED;
    }
}
