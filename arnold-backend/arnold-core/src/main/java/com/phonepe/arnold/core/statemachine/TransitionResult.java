package com.phonepe.arnold.core.statemachine;

/**
 * Outcome of a {@link StateMachine#tryFire} call. Lets lenient callers (GitLab sync, the analysis
 * orchestrator) distinguish a real transition from a benign no-op without catching exceptions.
 *
 * <ul>
 *   <li>{@link Status#TRANSITIONED} — the entity moved (or was already idempotently in the target);
 *       {@link #state()} is the committed state.</li>
 *   <li>{@link Status#IGNORED_NO_TRANSITION} — no edge for {@code (currentState, event)};
 *       {@link #state()} is the unchanged current state.</li>
 *   <li>{@link Status#IGNORED_GUARD_REJECTED} — an edge existed but its guard rejected the event
 *       (e.g. a stale analysis-run callback); state unchanged.</li>
 *   <li>{@link Status#IGNORED_TERMINAL} — the entity is in a terminal state and absorbs no events
 *       (e.g. a late close after a merge); state unchanged.</li>
 * </ul>
 */
public record TransitionResult<S extends Enum<S>, D>(
        Status status,
        StateInfo<S, D> state
) {
    public enum Status {
        TRANSITIONED,
        IGNORED_NO_TRANSITION,
        IGNORED_GUARD_REJECTED,
        IGNORED_TERMINAL
    }

    public boolean transitioned() {
        return status == Status.TRANSITIONED;
    }

    public boolean ignored() {
        return status != Status.TRANSITIONED;
    }
}
