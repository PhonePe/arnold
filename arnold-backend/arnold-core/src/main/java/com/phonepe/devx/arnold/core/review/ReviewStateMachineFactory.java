package com.phonepe.devx.arnold.core.review;

import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.actions.AnalysisCompletedAction;
import com.phonepe.devx.arnold.core.review.actions.ChangeClosedAction;
import com.phonepe.devx.arnold.core.review.actions.ChangeReopenedAction;
import com.phonepe.devx.arnold.core.review.actions.InvokeAnalysisAction;
import com.phonepe.devx.arnold.core.review.guards.AnalysisRunMatchesGuard;
import com.phonepe.devx.arnold.core.statemachine.ActionResolver;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;

/**
 * Builds the concrete state machine that realizes Arnold's MR review lifecycle. This is where the
 * generic engine is "instantiated for a flow": a different forge or review strategy would supply its
 * own factory/table over the same {@link StateMachine} engine.
 *
 * <h2>Source-of-truth handling</h2>
 * The version-control provider is authoritative for merge/close/reopen, so those are modeled as <b>global</b> transitions:
 * {@code CHANGE_MERGED} and {@code CHANGE_CLOSED} can fire from any non-terminal state, not just
 * {@code REVIEW_READY}. They must be driven via {@code tryFire} so a stale/duplicate/inapplicable fact
 * is ignored rather than rejected. {@code MERGED} is terminal; {@code CLOSED} is not (it can be
 * reopened).
 *
 * <h2>Transition table</h2>
 * <pre>
 *   CREATE        -- INVOKE_ANALYSIS -->     ANALYSING                    (user: start)
 *   ANALYSING     -- ANALYSIS_COMPLETED -->  ANALYSED                     (internal, guarded by runId)
 *   ANALYSED      -- MARK_REVIEW_READY -->   REVIEW_READY                 (user: publish)
 *   ANALYSED      -- INVOKE_ANALYSIS -->     ANALYSING                    (user: re-run)
 *   REVIEW_READY  -- INVOKE_ANALYSIS -->     ANALYSING                    (user: re-run)
 *   CLOSED        -- CHANGE_REOPENED -->     {CREATED|ANALYSED|REVIEW_READY} (sync: restore pre-close)
 *
 *   [global]      -- CHANGE_MERGED -->       MERGED   (sync, any non-terminal; MERGED is terminal)
 *   [global]      -- CHANGE_CLOSED -->       CLOSED   (sync, any non-terminal; records pre-close state)
 * </pre>
 */
public class ReviewStateMachineFactory {

    public static final String MACHINE_NAME = "mr-review";

    private final StateStore<ReviewState, ReviewStateData> stateStore;
    private final ActionResolver actionResolver;

    public ReviewStateMachineFactory(final StateStore<ReviewState, ReviewStateData> stateStore,
                                     final ActionResolver actionResolver) {
        this.stateStore = stateStore;
        this.actionResolver = actionResolver;
    }

    public StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> create() {
        return StateMachine.<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>builder(MACHINE_NAME)
                .store(stateStore)
                .actionResolver(actionResolver)

                .terminal(ReviewState.MERGED)

                .transition(ReviewState.CREATED, ReviewEvent.INVOKE_ANALYSIS,
                        InvokeAnalysisAction.class, ReviewState.ANALYSING)

                .guardedTransition(ReviewState.ANALYSING, ReviewEvent.ANALYSIS_COMPLETED,
                        AnalysisRunMatchesGuard.class, AnalysisCompletedAction.class, ReviewState.ANALYSED)

                .move(ReviewState.ANALYSED, ReviewEvent.MARK_REVIEW_READY, ReviewState.REVIEW_READY)
                .transition(ReviewState.ANALYSED, ReviewEvent.INVOKE_ANALYSIS,
                        InvokeAnalysisAction.class, ReviewState.ANALYSING)

                .transition(ReviewState.REVIEW_READY, ReviewEvent.INVOKE_ANALYSIS,
                        InvokeAnalysisAction.class, ReviewState.ANALYSING)

                .transition(ReviewState.CLOSED, ReviewEvent.CHANGE_REOPENED,
                        ChangeReopenedAction.class,
                        ReviewState.CREATED, ReviewState.ANALYSED, ReviewState.REVIEW_READY)

                .globalMove(ReviewEvent.CHANGE_MERGED, ReviewState.MERGED)
                .globalTransition(ReviewEvent.CHANGE_CLOSED, ChangeClosedAction.class, ReviewState.CLOSED)

                .build();
    }
}
