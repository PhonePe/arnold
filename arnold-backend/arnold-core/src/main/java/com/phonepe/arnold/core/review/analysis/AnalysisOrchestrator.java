package com.phonepe.arnold.core.review.analysis;

import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.InvokeAnalysis;

/**
 * Orchestrates the asynchronous {@code ANALYSING → ANALYSED} phase that sits <i>beside</i> the state
 * machine. The state machine treats analysis as a single coarse state; this orchestrator owns the
 * fine-grained saga underneath it:
 *
 * <ol>
 *   <li>{@link #startAnalysis} creates a durable {@code AnalysisRun}, fans out one review per
 *       persona, and returns the {@code runId} the {@code ANALYSING} state will carry.</li>
 *   <li>Each persona completion arrives later via a runner webhook and is recorded with
 *       {@link #recordPersonaCompletion}; the call is idempotent per persona and fenced by {@code runId}
 *       so stale callbacks from a superseded re-run are ignored.</li>
 *   <li>When the last persona resolves, the orchestrator collates the results and fires a single
 *       {@code ANALYSIS_COMPLETED} event back into the state machine, carrying the run's
 *       {@link com.phonepe.arnold.core.model.review.AnalysisOutcome} (success / partial / failure).
 *       There is no separate failure event: a failed run still resolves to {@code ANALYSED}, and
 *       retry/alerting is decided outside the machine.</li>
 * </ol>
 *
 * <p>The completion event is fired via the machine's lenient {@code tryFire}: if the MR was closed
 * upstream while analysis was in flight, the late callback is simply ignored. The join is derived from
 * the persisted per-persona run state — never from an in-memory combiner — so it survives restarts and
 * races. This interface is the seam for that implementation; the concrete orchestrator is out of scope
 * for this phase.
 */
public interface AnalysisOrchestrator {

    /**
     * Begins an analysis run for an MR and dispatches per-persona reviews.
     *
     * @return the newly created run's id, to be stored in the {@code ANALYSING} state data.
     */
    String startAnalysis(String mrId, InvokeAnalysis request);

    /**
     * Records the outcome of a single persona's review. Idempotent per {@code (runId, personaId)}
     * and a no-op when {@code runId} does not match the MR's in-flight run.
     */
    void recordPersonaCompletion(String mrId, PersonaReviewResult result);
}
