package com.phonepe.devx.arnold.core.review.actions;

import com.phonepe.devx.arnold.core.review.ReviewEvent;
import com.phonepe.devx.arnold.core.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.review.ReviewState;
import com.phonepe.devx.arnold.core.review.ReviewStateData;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.TransitionAction;
import com.phonepe.devx.arnold.core.statemachine.TransitionContext;

import javax.inject.Singleton;
import java.time.Instant;

/**
 * Action behind {@code CLOSED --GITLAB_REOPENED--> {CREATED|ANALYSED|REVIEW_READY}}. Restores the state
 * the MR held immediately before it was closed (captured in {@code preCloseState} by
 * {@link GitlabClosedAction}). Defaults to {@code CREATED} when no pre-close state was recorded. The
 * multi-target {@code to} set is exactly why the engine resolves the landing state from the action
 * rather than the transition declaration.
 */
@Singleton
public class GitlabReopenedAction
        implements TransitionAction<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    @Override
    public StateInfo<ReviewState, ReviewStateData> execute(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        final var previousData = context.fromData() == null ? ReviewStateData.initial() : context.fromData();
        final var restored = previousData.preCloseState() == null ? ReviewState.CREATED : previousData.preCloseState();
        final var data = previousData.toBuilder()
                .preCloseState(null)
                .updatedAt(Instant.now())
                .build();
        return StateInfo.of(restored, data);
    }
}
