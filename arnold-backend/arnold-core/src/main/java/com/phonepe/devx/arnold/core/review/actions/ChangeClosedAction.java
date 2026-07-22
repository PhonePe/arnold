package com.phonepe.devx.arnold.core.review.actions;

import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.TransitionAction;
import com.phonepe.devx.arnold.core.statemachine.TransitionContext;

import javax.inject.Singleton;
import java.time.Instant;

/**
 * Action behind the global {@code CHANGE_CLOSED} transition. The provider is the source of truth, so a close
 * can land from any non-terminal state. We record where the MR <i>was</i> (sanitized) into
 * {@code preCloseState} so a later {@code CHANGE_REOPENED} can restore meaningful context.
 *
 * <p>Sanitization: an in-flight {@code ANALYSING} run is abandoned on close, so its pre-close state is
 * recorded as {@code CREATED} (reopen starts fresh). {@code ANALYSED}/{@code REVIEW_READY}/{@code CREATED}
 * are preserved as-is so reopen returns there with findings intact.
 */
@Singleton
public class ChangeClosedAction
        implements TransitionAction<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> {

    @Override
    public StateInfo<ReviewState, ReviewStateData> execute(
            final TransitionContext<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> context) {
        final var previousState = context.fromState();
        final var previousData = context.fromData() == null ? ReviewStateData.initial() : context.fromData();
        final var data = previousData.toBuilder()
                .preCloseState(sanitize(previousState))
                .updatedAt(Instant.now())
                .build();
        return StateInfo.of(ReviewState.CLOSED, data);
    }

    private ReviewState sanitize(final ReviewState previousState) {
        return previousState == ReviewState.ANALYSING ? ReviewState.CREATED : previousState;
    }
}
