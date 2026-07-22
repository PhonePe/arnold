package com.phonepe.devx.arnold.core.review.actions;

import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload.InvokeAnalysis;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.TransitionAction;
import com.phonepe.devx.arnold.core.statemachine.TransitionContext;

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
