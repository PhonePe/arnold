package com.phonepe.devx.arnold.core.review;

import java.util.List;

/**
 * Event payloads ({@code P}) for the review machine, modeled as a sealed hierarchy so each
 * {@link ReviewEvent} can carry strongly-typed data while the engine stays generic over a single
 * payload type. Actions pattern-match on the concrete payload they expect.
 *
 * <p>Events that need no data ({@code MARK_REVIEW_READY}, {@code GITLAB_MERGED}, {@code GITLAB_REOPENED})
 * may fire with a {@code null} payload.
 */
public sealed interface ReviewEventPayload {

    /**
     * Raised by an author/reviewer to (re)start analysis. Carries the personas the analysis should run
     * against; the action hands these to the orchestrator to fan out CIBot reviews.
     */
    record InvokeAnalysis(List<String> personaIds, String invokedBy) implements ReviewEventPayload {
    }

    /**
     * Emitted internally by the orchestrator once an analysis run has resolved — regardless of whether
     * it succeeded, partially succeeded, or failed. The {@code outcome} rides into {@code ANALYSED} so
     * the UI can reflect it and out-of-band logic can decide on retries; the machine itself always
     * lands in {@code ANALYSED}. Guarded by {@code analysisRunId} so stale callbacks are ignored.
     */
    record AnalysisCompleted(String analysisRunId,
                             int personaCount,
                             AnalysisOutcome outcome,
                             String detail) implements ReviewEventPayload {
    }

    /**
     * Raised by GitLab sync when the MR is merged upstream. {@code mergeCommitSha} may be null if not
     * provided by the webhook.
     */
    record GitlabMerged(String mergeCommitSha) implements ReviewEventPayload {
    }

    /** Raised by GitLab sync when the MR is closed upstream (without merging). */
    record GitlabClosed(String closedBy) implements ReviewEventPayload {
    }

    /** Raised by GitLab sync when a previously closed MR is reopened upstream. */
    record GitlabReopened(String reopenedBy) implements ReviewEventPayload {
    }
}
