package com.phonepe.devx.arnold.core.review.guards;

import com.phonepe.devx.arnold.core.review.ReviewEvent;
import com.phonepe.devx.arnold.core.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.review.ReviewEventPayload.AnalysisCompleted;
import com.phonepe.devx.arnold.core.review.ReviewState;
import com.phonepe.devx.arnold.core.review.ReviewStateData;
import com.phonepe.devx.arnold.core.statemachine.TransitionContext;
import com.phonepe.devx.arnold.core.statemachine.TransitionGuard;

import javax.inject.Singleton;
import java.util.Objects;

/**
 * Guards {@code ANALYSING --ANALYSIS_COMPLETED--> ANALYSED} so it only fires when the event's
 * {@code analysisRunId} matches the run id currently stamped on the {@code ANALYSING} state. Without
 * this fence, a late completion from a run that was already superseded by a re-run could wrongly drive
 * the MR forward. Rejection here is benign — the lenient {@code tryFire} path reports it as
 * {@code IGNORED_GUARD_REJECTED} rather than an error.
 */
@Singleton
public class AnalysisRunMatchesGuard
        implements TransitionGuard<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    @Override
    public boolean isSatisfied(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        final var current = context.fromData();
        if (current == null || current.analysisRunId() == null) {
            return false;
        }
        if (context.payload() instanceof AnalysisCompleted completed) {
            return Objects.equals(current.analysisRunId(), completed.analysisRunId());
        }
        return false;
    }
}
