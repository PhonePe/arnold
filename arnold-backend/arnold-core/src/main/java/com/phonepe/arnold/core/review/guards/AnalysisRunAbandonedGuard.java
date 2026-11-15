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

package com.phonepe.arnold.core.review.guards;

import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.arnold.core.statemachine.TransitionContext;
import com.phonepe.arnold.core.statemachine.TransitionGuard;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Guards {@code ANALYSING --INVOKE_ANALYSIS--> ANALYSING} so a re-run may only supersede an in-flight run
 * that will never complete (e.g. its Clio pipeline failed before reporting back). Without this escape
 * hatch such an MR would be stuck in {@code ANALYSING} forever.
 */
@Singleton
public class AnalysisRunAbandonedGuard
        implements TransitionGuard<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    private final AnalysisOrchestrator orchestrator;

    @Inject
    public AnalysisRunAbandonedGuard(final AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public boolean isSatisfied(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        final var current = context.fromData();
        return current == null || current.analysisRunId() == null || orchestrator.isAbandoned(current.analysisRunId());
    }
}
