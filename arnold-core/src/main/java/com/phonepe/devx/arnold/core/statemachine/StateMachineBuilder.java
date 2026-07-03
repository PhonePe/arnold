package com.phonepe.devx.arnold.core.statemachine;

import com.phonepe.devx.arnold.core.storage.memory.InMemoryStateStore;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for a {@link StateMachine}. Each review flow uses this to declare its own transition
 * table — this is the "instantiate the right machine for your flow" seam referenced in the design.
 *
 * <p>Defaults make it usable in tests with zero wiring: an {@link InMemoryStateStore} and a reflective
 * {@link ActionResolver} (no-arg instantiation). Production wiring overrides both with the Guice-backed
 * store and resolver.
 */
public class StateMachineBuilder<S extends Enum<S>, E extends Enum<E>, D, P> {

    private final String name;
    private final Map<TransitionKey<S, E>, Transition<S, E, D, P>> transitions = new HashMap<>();
    private final Map<E, Transition<S, E, D, P>> globalTransitions = new HashMap<>();
    private final Set<S> terminalStates = new HashSet<>();
    private final List<TransitionListener<S, E, D, P>> listeners = new ArrayList<>();
    private StateStore<S, D> store = new InMemoryStateStore<>();
    private ActionResolver actionResolver = ReflectiveActionResolver.INSTANCE;

    StateMachineBuilder(final String name) {
        this.name = name;
    }

    public StateMachineBuilder<S, E, D, P> store(final StateStore<S, D> store) {
        this.store = store;
        return this;
    }

    public StateMachineBuilder<S, E, D, P> actionResolver(final ActionResolver actionResolver) {
        this.actionResolver = actionResolver;
        return this;
    }

    public StateMachineBuilder<S, E, D, P> listener(final TransitionListener<S, E, D, P> listener) {
        this.listeners.add(listener);
        return this;
    }

    /** Marks states as terminal: they absorb no events, and global transitions skip them. */
    @SafeVarargs
    public final StateMachineBuilder<S, E, D, P> terminal(final S... states) {
        for (final var state : states) {
            terminalStates.add(state);
        }
        return this;
    }

    public StateMachineBuilder<S, E, D, P> transition(final Transition<S, E, D, P> transition) {
        final var existing = transitions.putIfAbsent(transition.key(), transition);
        if (existing != null) {
            throw new IllegalStateException(
                    "Duplicate transition for %s --%s-->; (from, event) must be unique"
                            .formatted(transition.from(), transition.on()));
        }
        return this;
    }

    /** Declares an action-driven transition with one or more allowed target states. */
    @SafeVarargs
    public final StateMachineBuilder<S, E, D, P> transition(
            final S from,
            final E on,
            final Class<? extends TransitionAction<S, E, D, P>> action,
            final S... to) {
        return transition(new Transition<>(from, on, Set.of(to), action, null));
    }

    /** Declares an action-driven, guarded transition to a single target state. */
    public StateMachineBuilder<S, E, D, P> guardedTransition(
            final S from,
            final E on,
            final Class<? extends TransitionGuard<S, E, D, P>> guard,
            final Class<? extends TransitionAction<S, E, D, P>> action,
            final S to) {
        return transition(new Transition<>(from, on, Set.of(to), action, guard));
    }

    /** Declares a pure move (no action) from {@code from} to a single {@code to} state. */
    public StateMachineBuilder<S, E, D, P> move(final S from, final E on, final S to) {
        return transition(new Transition<>(from, on, Set.of(to), null, null));
    }

    /**
     * Declares a global transition: it fires on {@code on} from any non-terminal state that is not
     * already in {@code to}. Used to absorb authoritative out-of-band facts (e.g. GitLab merge/close).
     * The {@code from} stored on the underlying {@link Transition} is a sentinel and is never matched
     * directly. Global events are matched leniently and should be driven via {@code tryFire}.
     */
    @SafeVarargs
    public final StateMachineBuilder<S, E, D, P> globalTransition(
            final E on,
            final Class<? extends TransitionAction<S, E, D, P>> action,
            final S... to) {
        if (globalTransitions.containsKey(on)) {
            throw new IllegalStateException("Duplicate global transition for event " + on);
        }
        globalTransitions.put(on, new Transition<>(null, on, Set.of(to), action, null));
        return this;
    }

    /** Declares a global pure move to a single target state (no action). */
    public StateMachineBuilder<S, E, D, P> globalMove(final E on, final S to) {
        if (globalTransitions.containsKey(on)) {
            throw new IllegalStateException("Duplicate global transition for event " + on);
        }
        globalTransitions.put(on, new Transition<>(null, on, Set.of(to), null, null));
        return this;
    }

    public StateMachine<S, E, D, P> build() {
        return new StateMachine<>(name, transitions, globalTransitions, terminalStates, store, actionResolver, listeners);
    }
}
