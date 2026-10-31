package com.phonepe.devx.arnold.core.statemachine;

/**
 * Immutable snapshot of an entity's position in a state machine: the {@code state} it currently
 * occupies and the {@code data} associated with that state (e.g. analysis-run metadata while
 * {@code ANALYSING}). Actions return a new {@code StateInfo} to describe the post-transition state.
 */
public record StateInfo<S extends Enum<S>, D>(
        S state,
        D data
) {
    public static <S extends Enum<S>, D> StateInfo<S, D> of(final S state, final D data) {
        return new StateInfo<>(state, data);
    }

    public static <S extends Enum<S>, D> StateInfo<S, D> of(final S state) {
        return new StateInfo<>(state, null);
    }
}
