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

package com.phonepe.arnold.core.model.analysis;

import com.phonepe.arnold.models.analysis.AnalysisFindingResponse;
import com.phonepe.arnold.models.analysis.TokenUsage;

import java.util.List;

/**
 * Outcome of one persona's review within an analysis run, as delivered by the runner completion
 * webhook. {@code runId} fences the result against the MR's current in-flight run so callbacks from a
 * superseded re-run can be dropped. The orchestrator aggregates these per-persona outcomes into the
 * run-level {@link com.phonepe.arnold.core.model.review.AnalysisOutcome} it reports to the machine.
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
