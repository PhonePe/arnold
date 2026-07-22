package com.phonepe.devx.arnold.core.storage;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;
import com.phonepe.devx.arnold.core.statemachine.store.VersionedState;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Adapts any crud-store {@link ConcurrentStore} into the engine's {@link StateStore}, translating
 * between the persisted item {@code T} and the machine's {@link StateInfo}. The store's opaque version
 * token {@code V} (Aerospike record generation, an ETag, …) is carried straight through as the engine's
 * concurrency version, giving true optimistic locking backed by the datastore.
 *
 * <p>Mapping is supplied by the caller so this stays domain-agnostic:
 * <ul>
 *   <li>{@code toItem}: {@code (entityId, StateInfo) -> T} — build the persisted record;</li>
 *   <li>{@code toStateInfo}: {@code T -> StateInfo} — reconstruct engine state from a record.</li>
 * </ul>
 *
 * @param <T> persisted item type (must implement {@link Id})
 * @param <V> store's opaque version token
 * @param <S> state enum type
 * @param <D> per-state data type
 */
@Slf4j
public class ConcurrentStoreStateStore<T extends Id, V, S extends Enum<S>, D> implements StateStore<S, D> {

    private final ConcurrentStore<T, V> delegate;
    private final BiFunction<String, StateInfo<S, D>, T> toItem;
    private final Function<T, StateInfo<S, D>> toStateInfo;

    public ConcurrentStoreStateStore(final ConcurrentStore<T, V> delegate,
                                     final BiFunction<String, StateInfo<S, D>, T> toItem,
                                     final Function<T, StateInfo<S, D>> toStateInfo) {
        this.delegate = delegate;
        this.toItem = toItem;
        this.toStateInfo = toStateInfo;
    }

    @Override
    public Optional<VersionedState<S, D>> get(final String entityId) {
        return delegate.getWithVersion(entityId)
                .map(versioned -> new VersionedState<>(toStateInfo.apply(versioned.value()), versioned.version()));
    }

    @Override
    public VersionedState<S, D> initialize(final String entityId, final StateInfo<S, D> initial) {
        final var existing = get(entityId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            delegate.create(toItem.apply(entityId, initial));
        } catch (Exception e) {
            // Lost the create race: another writer initialized first. Fall through to a re-read.
            log.debug("initialize race for entity={}, re-reading: {}", entityId, e.getMessage());
        }
        return get(entityId).orElseThrow(() -> new IllegalStateException(
                "Failed to initialize entity " + entityId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean checkAndSet(final String entityId, final Object expectedVersion, final StateInfo<S, D> next) {
        try {
            delegate.checkAndUpdate(toItem.apply(entityId, next), (V) expectedVersion);
            return true;
        } catch (ConcurrentUpdateException e) {
            log.debug("version conflict on checkAndSet for entity={}: {}", entityId, e.getMessage());
            return false;
        }
    }
}
