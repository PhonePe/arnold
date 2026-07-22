package com.phonepe.devx.arnold.core.statemachine;

/**
 * A trigger fed into the state machine. The {@code type} (an enum) selects the transition to run,
 * while {@code payload} carries event-specific data the action needs (e.g. the collated persona
 * results behind {@code ANALYSIS_COMPLETED}, or provider merge info behind {@code CHANGE_MERGED}).
 */
public record TransitionEvent<E extends Enum<E>, P>(
        E type,
        P payload
) {
    public static <E extends Enum<E>, P> TransitionEvent<E, P> of(final E type, final P payload) {
        return new TransitionEvent<>(type, payload);
    }

    public static <E extends Enum<E>, P> TransitionEvent<E, P> of(final E type) {
        return new TransitionEvent<>(type, null);
    }
}
