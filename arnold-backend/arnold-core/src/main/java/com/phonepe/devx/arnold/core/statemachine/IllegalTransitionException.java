package com.phonepe.devx.arnold.core.statemachine;

/**
 * Thrown when an event is fired against a state that has no matching transition, i.e. the
 * {@code (from, event)} pair is not present in the machine's transition table.
 */
public class IllegalTransitionException extends RuntimeException {

    public <S extends Enum<S>, E extends Enum<E>> IllegalTransitionException(final String entityId,
                                                                             final S from,
                                                                             final E event) {
        super("No transition defined for entity=%s in state=%s on event=%s".formatted(entityId, from, event));
    }
}
