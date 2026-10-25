package com.phonepe.arnold.core.review.actions;

import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.AnalysisCompleted;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.statemachine.StateInfo;
import com.phonepe.arnold.core.statemachine.TransitionAction;
import com.phonepe.arnold.core.statemachine.TransitionContext;

import javax.inject.Singleton;
import java.time.Instant;

/**
 * Action behind {@code ANALYSING --ANALYSIS_COMPLETED--> ANALYSED}. Stamps the resolved run's id,
 * persona count and {@link com.phonepe.arnold.core.model.review.AnalysisOutcome} onto the
 * {@code ANALYSED} state. A failed/partial outcome still lands in {@code ANALYSED}; reacting to it
 * (retry, alert) is handled outside the state machine. Persisting collated feedback to Elasticsearch is
 * a post-transition concern handled by a listener / the orchestrator, not here.
 */
@Singleton
public class AnalysisCompletedAction
        implements TransitionAction<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    @Override
    public StateInfo<ReviewState, ReviewStateData> execute(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        if (!(context.payload() instanceof AnalysisCompleted completed)) {
            throw new IllegalArgumentException("ANALYSIS_COMPLETED requires an AnalysisCompleted payload");
        }
        final var data = ReviewStateData.builder()
                .analysisRunId(completed.analysisRunId())
                .personaCount(completed.personaCount())
                .analysisOutcome(completed.outcome())
                .outcomeDetail(completed.detail())
                .updatedAt(Instant.now())
                .build();
        return StateInfo.of(ReviewState.ANALYSED, data);
    }
}
