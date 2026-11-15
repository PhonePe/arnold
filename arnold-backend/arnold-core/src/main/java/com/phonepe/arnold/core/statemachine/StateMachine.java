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

import com.phonepe.arnold.core.statemachine.store.StateStore;
import com.phonepe.arnold.core.statemachine.store.VersionedState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generic, table-driven state machine engine. It is intentionally agnostic of any particular domain:
 * the set of states ({@code S}), events ({@code E}), per-state data ({@code D}) and event payloads
 * ({@code P}) are all supplied by the caller via a {@link StateMachineBuilder}. Concrete review flows
 * build their own table on top of this single engine — see the {@code review} package.
 *
 * <h2>Specific vs global transitions</h2>
 * A <b>specific</b> transition is keyed on {@code (from, event)}. A <b>global</b> transition is keyed on
 * the event alone and applies from <i>any non-terminal</i> state — used to absorb authoritative,
 * out-of-band facts (e.g. "GitLab says this MR was merged/closed") that can legitimately arrive in
 * almost any state. Resolution precedence: a specific transition always wins; a global applies only
 * when no specific edge exists, the current state is non-terminal, and the current state is not already
 * one of the global's targets (so duplicate facts are no-ops).
 *
 * <h2>Strict vs lenient firing</h2>
 * <ul>
 *   <li>{@link #fire(String, TransitionEvent)} — strict. Intended for user/system commands that must
 *       be valid; an unhandled event throws ({@link IllegalTransitionException} /
 *       {@link GuardRejectedException}). Map these to 4xx at the edge.</li>
 *   <li>{@link #tryFire(String, TransitionEvent)} — lenient. Intended for source-of-truth syncs and
 *       internal async callbacks that may legitimately be stale or arrive in an unexpected state; it
 *       never throws for an unhandled event, returning an {@link TransitionResult} describing whether
 *       the entity moved or why it was ignored.</li>
 * </ul>
 *
 * <p><b>Concurrency:</b> the whole resolve→commit cycle is retried on version-check contention, so
 * actions may run more than once and should be idempotent. Each retry re-reads the current state (and
 * its version) and re-resolves the transition, so a competing writer that advanced the entity simply
 * re-evaluates the event against the new state.
 */
@Slf4j
public class StateMachine<S extends Enum<S>, E extends Enum<E>, D, P> {

    private static final int MAX_CAS_RETRIES = 16;

    private final String name;
    private final Map<TransitionKey<S, E>, Transition<S, E, D, P>> transitions;
    private final Map<E, Transition<S, E, D, P>> globalTransitions;
    private final Set<S> terminalStates;
    private final StateStore<S, D> store;
    private final ActionResolver actionResolver;
    private final List<TransitionListener<S, E, D, P>> listeners;

    StateMachine(final String name,
                 final Map<TransitionKey<S, E>, Transition<S, E, D, P>> transitions,
                 final Map<E, Transition<S, E, D, P>> globalTransitions,
                 final Set<S> terminalStates,
                 final StateStore<S, D> store,
                 final ActionResolver actionResolver,
                 final List<TransitionListener<S, E, D, P>> listeners) {
        this.name = name;
        this.transitions = Map.copyOf(transitions);
        this.globalTransitions = Map.copyOf(globalTransitions);
        this.terminalStates = Set.copyOf(terminalStates);
        this.store = store;
        this.actionResolver = actionResolver;
        this.listeners = List.copyOf(listeners);
    }

    public static <S extends Enum<S>, E extends Enum<E>, D, P> StateMachineBuilder<S, E, D, P> builder(final String name) {
        return new StateMachineBuilder<>(name);
    }

    /**
     * Seeds an entity's initial state if it does not yet exist. Returns the effective current state
     * (the seeded value, or the pre-existing one if already initialized).
     */
    public StateInfo<S, D> initialize(final String entityId, final StateInfo<S, D> initial) {
        return store.initialize(entityId, initial).state();
    }

    public Optional<StateInfo<S, D>> currentState(final String entityId) {
        return store.get(entityId).map(VersionedState::state);
    }

    public boolean isTerminal(final S state) {
        return terminalStates.contains(state);
    }

    public boolean canFire(final S from, final E event) {
        return resolveTransition(from, event) != null;
    }

    /**
     * Strictly fires {@code event}: throws if the event is not handled in the current state. Use for
     * user/system commands that are expected to be valid.
     */
    public StateInfo<S, D> fire(final String entityId, final TransitionEvent<E, P> event) {
        final var result = tryFire(entityId, event);
        return switch (result.status()) {
            case TRANSITIONED -> result.state();
            case IGNORED_GUARD_REJECTED -> throw new GuardRejectedException(entityId, result.state().state(), event.type());
            case IGNORED_NO_TRANSITION, IGNORED_TERMINAL ->
                    throw new IllegalTransitionException(entityId, result.state().state(), event.type());
        };
    }

    /**
     * Leniently fires {@code event}: never throws for an unhandled event, instead reporting the outcome
     * via {@link TransitionResult}. Use for GitLab-sync facts and internal async callbacks that may be
     * stale or land in an unexpected state.
     */
    public TransitionResult<S, D> tryFire(final String entityId, final TransitionEvent<E, P> event) {
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            final var versioned = store.get(entityId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Entity %s is not initialized in state machine %s".formatted(entityId, name)));
            final var current = versioned.state();

            if (isTerminal(current.state())) {
                logIgnored(entityId, current, event, "state is terminal");
                return new TransitionResult<>(TransitionResult.Status.IGNORED_TERMINAL, current);
            }

            final var transition = resolveTransition(current.state(), event.type());
            if (transition == null) {
                logIgnored(entityId, current, event, "no matching transition");
                return new TransitionResult<>(TransitionResult.Status.IGNORED_NO_TRANSITION, current);
            }

            final var context = new TransitionContext<>(entityId, current, event);
            if (transition.hasGuard() && !resolveGuard(transition).isSatisfied(context)) {
                logIgnored(entityId, current, event, "guard rejected");
                return new TransitionResult<>(TransitionResult.Status.IGNORED_GUARD_REJECTED, current);
            }

            final var next = resolveNext(transition, context);
            if (!transition.allows(next.state())) {
                throw new InvalidTargetStateException(entityId, next.state(), transition.to());
            }

            if (store.checkAndSet(entityId, versioned.version(), next)) {
                notifyListeners(entityId, current, next, event);
                log.debug("[{}] entity={} transitioned {} --{}--> {}",
                        name, entityId, current.state(), event.type(), next.state());
                return new TransitionResult<>(TransitionResult.Status.TRANSITIONED, next);
            }
            log.debug("[{}] CAS contention on entity={} (attempt {}), retrying", name, entityId, attempt + 1);
        }
        throw new IllegalStateException(
                "Failed to fire event %s on entity %s after %d attempts due to contention"
                        .formatted(event.type(), entityId, MAX_CAS_RETRIES));
    }

    /**
     * Resolves which transition (if any) handles {@code event} from {@code from}: a specific edge wins;
     * otherwise a global edge applies when {@code from} is non-terminal and not already a target of the
     * global (the latter makes duplicate source-of-truth facts a no-op).
     */
    private Transition<S, E, D, P> resolveTransition(final S from, final E event) {
        final var specific = transitions.get(new TransitionKey<>(from, event));
        if (specific != null) {
            return specific;
        }
        if (isTerminal(from)) {
            return null;
        }
        final var global = globalTransitions.get(event);
        if (global != null && !global.allows(from)) {
            return global;
        }
        return null;
    }

    private StateInfo<S, D> resolveNext(final Transition<S, E, D, P> transition,
                                        final TransitionContext<S, E, D, P> context) {
        if (transition.action() != null) {
            return actionResolver.resolve(transition.action()).execute(context);
        }
        // No action: a pure move is only well-defined when exactly one target is declared.
        if (transition.to().size() != 1) {
            throw new InvalidTargetStateException(context.entityId(), null, transition.to());
        }
        return new StateInfo<>(transition.to().iterator().next(), context.fromData());
    }

    private TransitionGuard<S, E, D, P> resolveGuard(final Transition<S, E, D, P> transition) {
        return actionResolver.resolve(transition.guard());
    }

    private void notifyListeners(final String entityId,
                                 final StateInfo<S, D> from,
                                 final StateInfo<S, D> to,
                                 final TransitionEvent<E, P> event) {
        for (final var listener : listeners) {
            try {
                listener.onTransition(entityId, from, to, event);
            } catch (Exception e) {
                log.error("[{}] listener {} failed for entity={} on {} --{}--> {}",
                        name, listener.getClass().getSimpleName(), entityId, from.state(), event.type(), to.state(), e);
            }
        }
    }

    private void logIgnored(final String entityId,
                            final StateInfo<S, D> current,
                            final TransitionEvent<E, P> event,
                            final String reason) {
        log.debug("[{}] ignoring event {} for entity={} in state={}: {}",
                name, event.type(), entityId, current.state(), reason);
    }
}
