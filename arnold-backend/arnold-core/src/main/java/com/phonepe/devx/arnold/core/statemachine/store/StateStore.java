package com.phonepe.devx.arnold.core.statemachine.store;

import com.phonepe.devx.arnold.core.statemachine.StateInfo;

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
