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

package com.phonepe.arnold.core.statemachine.store;

import com.phonepe.arnold.core.statemachine.StateInfo;

import java.util.Optional;

/**
 * Durability + concurrency seam for state-machine state, modeled on optimistic version-based
 * compare-and-set. The engine never mutates state in place; it reads the current state <i>and its
 * version</i>, computes the next state, and commits only if the version is unchanged — serializing
 * concurrent events for the same entity (e.g. a GitLab close racing an analysis callback) without a
 * global lock.
 *
 * <p>This maps directly onto crud-store's {@code ConcurrentStore} ({@code getWithVersion} +
 * {@code checkAndUpdate}); the in-memory implementation backs tests and the first phase. The version is
 * an opaque {@link Object} so backends can use their natural token (Aerospike generation {@code Long},
 * an ETag {@code String}, etc.) without leaking it into the engine.
 *
 * @param <S> state enum type
 * @param <D> per-state data type
 */
public interface StateStore<S extends Enum<S>, D> {

    /**
     * @return the current state and its concurrency version, or empty if the entity does not exist.
     */
    Optional<VersionedState<S, D>> get(String entityId);

    /**
     * Seeds the initial state for a new entity. Returns the seeded value on success, or the existing
     * value if the entity was already initialized (no overwrite).
     */
    VersionedState<S, D> initialize(String entityId, StateInfo<S, D> initial);

    /**
     * Atomically sets the entity to {@code next} only if its stored version still equals
     * {@code expectedVersion}. Returns {@code true} on success, {@code false} if the version has moved
     * on (concurrent modification) — signalling the engine to re-read and retry.
     */
    boolean checkAndSet(String entityId, Object expectedVersion, StateInfo<S, D> next);
}
