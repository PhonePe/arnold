package com.phonepe.devx.arnold.core.statemachine;

import java.util.Set;

/**
 * A single edge in the state machine, keyed by {@code (from, on)} — the state being left and the
 * event that triggers the move. {@code to} is the set of states the transition is allowed to land in;
 * the engine validates that the action's returned state belongs to this set. When {@code to} holds a
 * single state the engine can resolve the target without an action (a pure move).
 *
 * <p>{@code action} and {@code guard} are referenced by class and resolved at fire-time via an
 * {@link ActionResolver}, so they can be DI-managed singletons. {@code guard} may be {@code null}.
 */
public record Transition<S extends Enum<S>, E extends Enum<E>, D, P>(
        S from,
        E on,
        Set<S> to,
        Class<? extends TransitionAction<S, E, D, P>> action,
        Class<? extends TransitionGuard<S, E, D, P>> guard
) {
    public Transition {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException(
                    "Transition %s --%s--> must declare at least one target state".formatted(from, on));
        }
        to = Set.copyOf(to);
    }

    public boolean allows(final S candidate) {
        return to.contains(candidate);
    }

    public boolean hasGuard() {
        return guard != null;
    }

    public TransitionKey<S, E> key() {
        return new TransitionKey<>(from, on);
    }
}
