package com.phonepe.devx.arnold.core.statemachine;

/**
 * Thrown when an action returns a target state that is not among the transition's declared {@code to}
 * states. Indicates a bug in the action or transition table rather than a caller error.
 */
public class InvalidTargetStateException extends RuntimeException {

    public <S extends Enum<S>> InvalidTargetStateException(final String entityId,
                                                           final S resolved,
                                                           final Object allowed) {
        super("Action for entity=%s resolved to state=%s which is not in allowed targets=%s"
                .formatted(entityId, resolved, allowed));
    }
}
