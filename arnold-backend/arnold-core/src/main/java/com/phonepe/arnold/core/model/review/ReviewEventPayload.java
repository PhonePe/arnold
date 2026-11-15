/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.model.review;

import com.phonepe.arnold.models.analysis.ExecutionOptions;

import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;

/**
 * Event payloads ({@code P}) for the review machine, modeled as a sealed hierarchy so each
 * {@link ReviewEvent} can carry strongly-typed data while the engine stays generic over a single
 * payload type. Actions pattern-match on the concrete payload they expect.
 *
 * <p>Events that need no data ({@code MARK_REVIEW_READY},
 * {@code CHANGE_MERGED}, {@code CHANGE_REOPENED})
 * may fire with a {@code null} payload.
 */
public sealed interface ReviewEventPayload {

    /**
     * Raised by an author/reviewer to (re)start analysis. Carries the personas the analysis should run
     * against; the action hands these to the orchestrator to fan out reviews.
     */
    record InvokeAnalysis(List<String> personaIds, String invokedBy, String rfcUrl,
                          List<String> jiraTickets, String reviewContext, List<ReviewPersonaSnapshot> reviewPersonas,
                          String requestFingerprint,
                          ExecutionOptions execution) implements ReviewEventPayload {
        public InvokeAnalysis(List<String> personaIds, String invokedBy, String rfcUrl,
                              List<String> jiraTickets, String reviewContext, List<ReviewPersonaSnapshot> reviewPersonas,
                              String requestFingerprint) {
            this(personaIds, invokedBy, rfcUrl, jiraTickets, reviewContext, reviewPersonas, requestFingerprint, null);
        }
        public InvokeAnalysis(final List<String> personaIds, final String invokedBy) {
            this(personaIds, invokedBy, null, List.of(), null, List.of(), null);
        }
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
     * Raised by provider sync when a change request is merged. {@code mergeCommitSha} may be null.
     */
    record ChangeMerged(String mergeCommitSha) implements ReviewEventPayload {
    }

    /** Raised by provider sync when a change request is closed without merging. */
    record ChangeClosed(String closedBy) implements ReviewEventPayload {
    }

    /** Raised by provider sync when a previously closed change request is reopened. */
    record ChangeReopened(String reopenedBy) implements ReviewEventPayload {
    }
}
