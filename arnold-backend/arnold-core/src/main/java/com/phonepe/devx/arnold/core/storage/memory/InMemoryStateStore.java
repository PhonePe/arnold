package com.phonepe.devx.arnold.core.storage.memory;

import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;
import com.phonepe.devx.arnold.core.statemachine.store.VersionedState;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link StateStore} backed by a {@link ConcurrentHashMap}, using a monotonically increasing
 * {@code Long} as the opaque version stamp. Intended for the first phase and for tests; not durable
 * across restarts. Atomicity comes from the map's per-key atomic operations, giving the same
 * version-check semantics a durable {@code ConcurrentStore} backend would.
 */
public class InMemoryStateStore<S extends Enum<S>, D> implements StateStore<S, D> {

    private record Entry<S extends Enum<S>, D>(StateInfo<S, D> state, long version) {
    }

    private final ConcurrentHashMap<String, Entry<S, D>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<VersionedState<S, D>> get(final String entityId) {
        return Optional.ofNullable(store.get(entityId))
                .map(entry -> new VersionedState<>(entry.state(), entry.version()));
    }

    @Override
    public VersionedState<S, D> initialize(final String entityId, final StateInfo<S, D> initial) {
        final var entry = store.computeIfAbsent(entityId, ignored -> new Entry<>(initial, 0L));
        return new VersionedState<>(entry.state(), entry.version());
    }

    @Override
    public boolean checkAndSet(final String entityId, final Object expectedVersion, final StateInfo<S, D> next) {
        final var current = store.get(entityId);
        if (current == null || !Long.valueOf(current.version()).equals(expectedVersion)) {
            return false;
        }
        return store.replace(entityId, current, new Entry<>(next, current.version() + 1));
    }
}
