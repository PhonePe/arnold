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
 * The side-effecting work performed while moving an entity from one state to the next. An action
 * receives the {@link TransitionContext} (from-state + triggering event) and returns the
 * {@link StateInfo} the entity should land in. The returned {@code state} must be one of the
 * transition's declared target states; the engine enforces this before persisting.
 *
 * <p>Actions are resolved per transition via an {@link ActionResolver}, so they are free to be
 * dependency-injected singletons (e.g. to reach the {@code AnalysisOrchestrator} or
 * {@code ReviewFeedbackService}). Implementations should be idempotent where possible, since the
 * engine may retry on optimistic-concurrency contention.
 */
@FunctionalInterface
public interface TransitionAction<S extends Enum<S>, E extends Enum<E>, D, P> {

    StateInfo<S, D> execute(TransitionContext<S, E, D, P> context);
}
