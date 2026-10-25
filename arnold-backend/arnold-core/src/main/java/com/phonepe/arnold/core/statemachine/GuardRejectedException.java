package com.phonepe.arnold.core.statemachine;

/**
 * Thrown when a transition's {@link TransitionGuard} rejects the event. The entity is left untouched
 * in its current state.
 */
public class GuardRejectedException extends RuntimeException {

    public <S extends Enum<S>, E extends Enum<E>> GuardRejectedException(final String entityId,
                                                                         final S from,
                                                                         final E event) {
        super("Guard rejected transition for entity=%s in state=%s on event=%s".formatted(entityId, from, event));
    }
}
