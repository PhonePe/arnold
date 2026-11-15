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

import com.phonepe.arnold.core.storage.memory.InMemoryStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the generic engine in isolation using a tiny self-contained machine, so the tests assert
 * engine semantics (table lookup, guards, target validation, CAS, listeners) rather than any review
 * domain behaviour.
 */
class StateMachineTest {

    private enum Light {RED, GREEN, YELLOW}

    private enum Signal {GO, CAUTION, STOP, TICK}

    private static final String ENTITY = "intersection-1";

    private InMemoryStateStore<Light, String> store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStateStore<>();
    }

    /** A trivial action that moves to a fixed target, echoing the incoming payload into the state data. */
    private static TransitionAction<Light, Signal, String, String> moveTo(final Light target) {
        return ctx -> StateInfo.of(target, ctx.payload());
    }

    private StateMachineBuilder<Light, Signal, String, String> baseBuilder() {
        return StateMachine.<Light, Signal, String, String>builder("traffic-light").store(store);
    }

    @Nested
    class HappyPath {

        @Test
        void testFire_shouldMoveToTarget_whenTransitionDefined() {
            final var machine = baseBuilder()
                    .actionResolver(resolverFor(moveTo(Light.GREEN)))
                    .transition(Light.RED, Signal.GO, greenAction(), Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, "init"));

            final var result = machine.fire(ENTITY, TransitionEvent.of(Signal.GO, "payload"));

            assertEquals(Light.GREEN, result.state());
            assertEquals("payload", result.data());
            assertEquals(Light.GREEN, machine.currentState(ENTITY).orElseThrow().state());
        }

        @Test
        void testFire_shouldResolveSingleTarget_whenPureMoveHasNoAction() {
            final var machine = baseBuilder()
                    .move(Light.GREEN, Signal.STOP, Light.RED)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.GREEN, "keep"));

            final var result = machine.fire(ENTITY, TransitionEvent.of(Signal.STOP));

            assertEquals(Light.RED, result.state());
            // pure move carries the prior data forward unchanged
            assertEquals("keep", result.data());
        }
    }

    @Nested
    class IllegalTransitions {

        @Test
        void testFire_shouldThrow_whenNoTransitionForEvent() {
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertThrows(IllegalTransitionException.class,
                    () -> machine.fire(ENTITY, TransitionEvent.of(Signal.STOP)));
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }

        @Test
        void testFire_shouldThrow_whenEntityNotInitialized() {
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN)
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> machine.fire(ENTITY, TransitionEvent.of(Signal.GO)));
        }

        @Test
        void testFire_shouldThrow_whenActionReturnsDisallowedTarget() {
            // action returns YELLOW but the transition only allows GREEN
            final var machine = baseBuilder()
                    .actionResolver(resolverFor(moveTo(Light.YELLOW)))
                    .transition(Light.RED, Signal.GO, greenAction(), Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertThrows(InvalidTargetStateException.class,
                    () -> machine.fire(ENTITY, TransitionEvent.of(Signal.GO)));
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }

        @Test
        void testBuild_shouldThrow_whenDuplicateTransitionKey() {
            final var builder = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN);

            assertThrows(IllegalStateException.class,
                    () -> builder.move(Light.RED, Signal.GO, Light.YELLOW));
        }
    }

    @Nested
    class Guards {

        @Test
        void testFire_shouldReject_whenGuardNotSatisfied() {
            final var machine = baseBuilder()
                    .actionResolver(resolver(java.util.Map.of(
                            GreenAction.class, moveTo(Light.GREEN),
                            DenyGuard.class, (TransitionGuard<Light, Signal, String, String>) ctx -> false)))
                    .guardedTransition(Light.RED, Signal.GO, DenyGuard.class, GreenAction.class, Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertThrows(GuardRejectedException.class,
                    () -> machine.fire(ENTITY, TransitionEvent.of(Signal.GO)));
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }

        @Test
        void testFire_shouldProceed_whenGuardSatisfied() {
            final var machine = baseBuilder()
                    .actionResolver(resolver(java.util.Map.of(
                            GreenAction.class, moveTo(Light.GREEN),
                            DenyGuard.class, (TransitionGuard<Light, Signal, String, String>) ctx -> true)))
                    .guardedTransition(Light.RED, Signal.GO, DenyGuard.class, GreenAction.class, Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(Light.GREEN, machine.fire(ENTITY, TransitionEvent.of(Signal.GO)).state());
        }
    }

    @Nested
    class Listeners {

        @Test
        void testFire_shouldNotifyListener_whenTransitionCommits() {
            final var observed = new ArrayList<String>();
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN)
                    .listener((id, from, to, event) ->
                            observed.add("%s:%s->%s".formatted(id, from.state(), to.state())))
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            machine.fire(ENTITY, TransitionEvent.of(Signal.GO));

            assertEquals(List.of(ENTITY + ":RED->GREEN"), observed);
        }

        @Test
        void testFire_shouldStillCommit_whenListenerThrows() {
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN)
                    .listener((id, from, to, event) -> {
                        throw new RuntimeException("listener boom");
                    })
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(Light.GREEN, machine.fire(ENTITY, TransitionEvent.of(Signal.GO)).state());
        }
    }

    @Nested
    class Concurrency {

        @Test
        void testInitialize_shouldNotOverwrite_whenAlreadyInitialized() {
            final var machine = baseBuilder().build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, "first"));

            final var second = machine.initialize(ENTITY, StateInfo.of(Light.GREEN, "second"));

            assertEquals(Light.RED, second.state());
            assertEquals("first", second.data());
        }

        @Test
        void testConcurrentFire_shouldApplyExactlyOnce_whenManyThreadsRace() throws Exception {
            // GREEN --STOP--> RED is the only legal move; N threads fire it, exactly one should win,
            // the rest must observe an illegal transition out of RED.
            final var machine = baseBuilder()
                    .move(Light.GREEN, Signal.STOP, Light.RED)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.GREEN, null));

            final int threads = 16;
            final var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            final var ready = new java.util.concurrent.CountDownLatch(threads);
            final var start = new java.util.concurrent.CountDownLatch(1);
            final var success = new AtomicInteger();
            final var illegal = new AtomicInteger();
            final var futures = new ArrayList<java.util.concurrent.Future<?>>();

            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    awaitQuietly(start);
                    try {
                        machine.fire(ENTITY, TransitionEvent.of(Signal.STOP));
                        success.incrementAndGet();
                    } catch (IllegalTransitionException e) {
                        illegal.incrementAndGet();
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (final var f : futures) {
                f.get();
            }
            pool.shutdownNow();

            assertEquals(1, success.get(), "exactly one fire should succeed");
            assertEquals(threads - 1, illegal.get(), "all other fires should be rejected");
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }
    }

    @Nested
    class GlobalsAndTerminals {

        /** A global edge fires from any non-terminal state that isn't already its target. */
        @Test
        void testTryFire_shouldApplyGlobal_fromAnyNonTerminalState() {
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.GO, Light.GREEN)
                    .globalMove(Signal.STOP, Light.RED)   // STOP → RED from anywhere
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.GREEN, null));

            final var result = machine.tryFire(ENTITY, TransitionEvent.of(Signal.STOP));

            assertEquals(TransitionResult.Status.TRANSITIONED, result.status());
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }

        /** A specific edge wins over a global for the same event. */
        @Test
        void testTryFire_shouldPreferSpecificOverGlobal() {
            final var machine = baseBuilder()
                    .move(Light.RED, Signal.STOP, Light.YELLOW)   // specific RED+STOP → YELLOW
                    .globalMove(Signal.STOP, Light.RED)           // global STOP → RED
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(Light.YELLOW, machine.tryFire(ENTITY, TransitionEvent.of(Signal.STOP)).state().state());
        }

        /** A global whose target equals the current state is a no-op (duplicate fact). */
        @Test
        void testTryFire_shouldIgnoreGlobal_whenAlreadyInTargetState() {
            final var machine = baseBuilder()
                    .globalMove(Signal.STOP, Light.RED)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(TransitionResult.Status.IGNORED_NO_TRANSITION,
                    machine.tryFire(ENTITY, TransitionEvent.of(Signal.STOP)).status());
        }

        /** Terminal states absorb nothing, including globals. */
        @Test
        void testTryFire_shouldIgnore_whenStateIsTerminal() {
            final var machine = baseBuilder()
                    .terminal(Light.RED)
                    .globalMove(Signal.STOP, Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(TransitionResult.Status.IGNORED_TERMINAL,
                    machine.tryFire(ENTITY, TransitionEvent.of(Signal.STOP)).status());
            assertEquals(Light.RED, machine.currentState(ENTITY).orElseThrow().state());
        }

        @Test
        void testTryFire_shouldReportGuardRejection_withoutThrowing() {
            final var machine = baseBuilder()
                    .actionResolver(resolver(java.util.Map.of(
                            GreenAction.class, moveTo(Light.GREEN),
                            DenyGuard.class, (TransitionGuard<Light, Signal, String, String>) ctx -> false)))
                    .guardedTransition(Light.RED, Signal.GO, DenyGuard.class, GreenAction.class, Light.GREEN)
                    .build();
            machine.initialize(ENTITY, StateInfo.of(Light.RED, null));

            assertEquals(TransitionResult.Status.IGNORED_GUARD_REJECTED,
                    machine.tryFire(ENTITY, TransitionEvent.of(Signal.GO)).status());
        }

        @Test
        void testBuild_shouldThrow_whenDuplicateGlobalEvent() {
            final var builder = baseBuilder().globalMove(Signal.STOP, Light.RED);
            assertThrows(IllegalStateException.class, () -> builder.globalMove(Signal.STOP, Light.GREEN));
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private static void awaitQuietly(final java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Named action class so guarded-transition declarations have a concrete type to resolve. */
    private static final class GreenAction implements TransitionAction<Light, Signal, String, String> {
        @Override
        public StateInfo<Light, String> execute(final TransitionContext<Light, Signal, String, String> ctx) {
            return StateInfo.of(Light.GREEN, ctx.payload());
        }
    }

    private static final class DenyGuard implements TransitionGuard<Light, Signal, String, String> {
        @Override
        public boolean isSatisfied(final TransitionContext<Light, Signal, String, String> ctx) {
            return false;
        }
    }

    private static Class<? extends TransitionAction<Light, Signal, String, String>> greenAction() {
        return GreenAction.class;
    }

    /** Resolver that returns the same action instance for any action class requested. */
    private static ActionResolver resolverFor(final TransitionAction<Light, Signal, String, String> action) {
        return new ActionResolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(final Class<T> type) {
                return (T) action;
            }
        };
    }

    /** Resolver backed by an explicit class→instance map, for mixing actions and guards. */
    private static ActionResolver resolver(final java.util.Map<Class<?>, Object> bindings) {
        return new ActionResolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(final Class<T> type) {
                final var instance = bindings.get(type);
                assertTrue(instance != null, "no binding for " + type);
                return (T) instance;
            }
        };
    }

    @Test
    void testCanFire_shouldReflectTable() {
        final var machine = baseBuilder()
                .move(Light.RED, Signal.GO, Light.GREEN)
                .build();

        assertTrue(machine.canFire(Light.RED, Signal.GO));
        assertFalse(machine.canFire(Light.RED, Signal.STOP));
    }

    @Test
    void testTransition_shouldRejectEmptyTargetSet() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transition<>(Light.RED, Signal.GO, Set.of(), greenAction(), null));
    }

    @Test
    void testReflectiveResolver_shouldInstantiateNoArgClass() {
        assertSame(GreenAction.class, ReflectiveActionResolver.INSTANCE.resolve(GreenAction.class).getClass());
    }
}
