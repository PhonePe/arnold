package com.phonepe.arnold.core.review.guards;

import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.AnalysisCompleted;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.statemachine.TransitionContext;
import com.phonepe.arnold.core.statemachine.TransitionGuard;

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
