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

import lombok.Builder;
import lombok.With;

import java.time.Instant;

/**
 * Per-state data ({@code D}) carried in the review machine's {@link com.phonepe.arnold.core.statemachine.StateInfo}.
 * Lightweight on purpose: the heavy analysis bookkeeping (per-persona progress) lives in the
 * orchestrator's own {@code AnalysisRun}; here we keep only what downstream states need.
 *
 * <ul>
 *   <li>{@code analysisRunId} / {@code personaCount} — identify the run that produced the current
 *       analysis.</li>
 *   <li>{@code analysisOutcome} / {@code outcomeDetail} — how the last analysis resolved (success,
 *       partial, or failure). Present from {@code ANALYSED} onward; the machine treats all outcomes as
 *       a successful arrival at {@code ANALYSED}.</li>
 *   <li>{@code preCloseState} — the state the change request held immediately before its provider closed it, captured
 *       so a later {@code CHANGE_REOPENED} can restore meaningful context instead of dropping the MR to
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
