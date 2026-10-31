package com.phonepe.devx.arnold.core.review.guards;

import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.devx.arnold.core.statemachine.TransitionContext;
import com.phonepe.devx.arnold.core.statemachine.TransitionGuard;

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
