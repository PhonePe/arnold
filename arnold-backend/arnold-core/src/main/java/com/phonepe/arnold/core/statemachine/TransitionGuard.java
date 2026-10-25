package com.phonepe.arnold.core.statemachine;

/**
 * Optional precondition guarding a transition. When a guard returns {@code false} the transition is
 * rejected and the action is never invoked. Use guards for cheap, side-effect-free checks (e.g.
 * "event carries a runId matching the in-flight analysis run"); keep mutation in the action.
 */
@FunctionalInterface
public interface TransitionGuard<S extends Enum<S>, E extends Enum<E>, D, P> {

    boolean isSatisfied(TransitionContext<S, E, D, P> context);
}
