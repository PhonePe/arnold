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

package com.phonepe.arnold.core.review.actions;

import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.statemachine.StateInfo;
import com.phonepe.arnold.core.statemachine.TransitionAction;
import com.phonepe.arnold.core.statemachine.TransitionContext;

import javax.inject.Singleton;
import java.time.Instant;

/**
 * Action behind the global {@code CHANGE_CLOSED} transition. The provider is the source of truth, so a close
 * can land from any non-terminal state. We record where the MR <i>was</i> (sanitized) into
 * {@code preCloseState} so a later {@code CHANGE_REOPENED} can restore meaningful context.
 *
 * <p>Sanitization: an in-flight {@code ANALYSING} run is abandoned on close, so its pre-close state is
 * recorded as {@code CREATED} (reopen starts fresh). Other states are preserved so reopen returns
 * there with findings intact.
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
