/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
