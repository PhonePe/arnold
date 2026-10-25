/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.review.actions;

import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.InvokeAnalysis;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.arnold.core.statemachine.StateInfo;
import com.phonepe.arnold.core.statemachine.TransitionAction;
import com.phonepe.arnold.core.statemachine.TransitionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

/**
 * Action behind {@code * --INVOKE_ANALYSIS--> ANALYSING}. Kicks off the async analysis saga via the
 * {@link AnalysisOrchestrator} and records the resulting run id on the {@code ANALYSING} state. The
 * orchestrator is responsible for later firing {@code ANALYSIS_COMPLETED} (carrying the run outcome).
 */
@Singleton
public class InvokeAnalysisAction
        implements TransitionAction<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    private final AnalysisOrchestrator orchestrator;

    @Inject
    public InvokeAnalysisAction(final AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public StateInfo<ReviewState, ReviewStateData> execute(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        if (!(context.payload() instanceof InvokeAnalysis request)) {
            throw new IllegalArgumentException("INVOKE_ANALYSIS requires an InvokeAnalysis payload");
        }
        final var runId = orchestrator.startAnalysis(context.entityId(), request);
        final var data = ReviewStateData.builder()
                .analysisRunId(runId)
                .personaCount(request.personaIds().size())
                .updatedAt(Instant.now())
                .build();
        return StateInfo.of(ReviewState.ANALYSING, data);
    }
}
