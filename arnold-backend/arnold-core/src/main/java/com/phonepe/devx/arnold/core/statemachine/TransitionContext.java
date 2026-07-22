package com.phonepe.devx.arnold.core.statemachine;

/**
 * Everything an action or guard needs to evaluate a single transition: the {@code entityId} being
 * transitioned (e.g. the MR id), the {@code from} state snapshot it is leaving, and the {@code event}
 * that triggered the transition. This is read-only; an action communicates its outcome purely through
 * the {@link StateInfo} it returns.
 */
public record TransitionContext<S extends Enum<S>, E extends Enum<E>, D, P>(
        String entityId,
        StateInfo<S, D> from,
        TransitionEvent<E, P> event
) {
    public S fromState() {
        return from.state();
    }

    public D fromData() {
        return from.data();
    }

    public E eventType() {
        return event.type();
    }

    public P payload() {
        return event.payload();
    }
}
