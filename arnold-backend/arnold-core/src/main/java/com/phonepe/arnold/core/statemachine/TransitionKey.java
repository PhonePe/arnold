package com.phonepe.arnold.core.statemachine;

/**
 * Composite lookup key for the transition table: a transition is uniquely identified by the state it
 * leaves and the event that triggers it. Two arrows out of the same state (e.g.
 * {@code ANALYSED --INVOKE_ANALYSIS--> ANALYSING} vs {@code ANALYSED --MARK_REVIEW_READY--> REVIEW_READY})
 * are distinguished purely by their event.
 */
public record TransitionKey<S extends Enum<S>, E extends Enum<E>>(
        S from,
        E on
) {
}
