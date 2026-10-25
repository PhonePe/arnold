package com.phonepe.arnold.core.storage;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Id;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.statemachine.StateInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ConcurrentStoreStateStore} maps engine state onto a crud-store {@link ConcurrentStore}
 * and honours version-based compare-and-set (a stale expected version is rejected, a fresh one wins).
 * Uses a minimal in-test {@code ConcurrentStore} so no real datastore is needed.
 */
class ConcurrentStoreStateStoreTest {

    private enum Flow {A, B}

    private record Item(String id, Flow state) implements Id {
    }

    private FakeConcurrentStore delegate;
    private ConcurrentStoreStateStore<Item, Long, Flow, Void> store;

    @BeforeEach
    void setUp() {
        delegate = new FakeConcurrentStore();
        store = new ConcurrentStoreStateStore<>(
                delegate,
                (id, stateInfo) -> new Item(id, stateInfo.state()),
                item -> StateInfo.of(item.state(), null));
    }

    @Nested
    class Initialization {

        @Test
        void testInitialize_shouldCreateItem_whenAbsent() {
            final var seeded = store.initialize("e1", StateInfo.of(Flow.A, null));

            assertEquals(Flow.A, seeded.state().state());
            assertEquals(Flow.A, delegate.get("e1").orElseThrow().state());
        }

        @Test
        void testInitialize_shouldNotOverwrite_whenAlreadyPresent() {
            store.initialize("e1", StateInfo.of(Flow.A, null));
            final var second = store.initialize("e1", StateInfo.of(Flow.B, null));

            assertEquals(Flow.A, second.state().state());
        }
    }

    @Nested
    class CompareAndSet {

        @Test
        void testCheckAndSet_shouldSucceed_whenVersionMatches() {
            store.initialize("e1", StateInfo.of(Flow.A, null));
            final var version = store.get("e1").orElseThrow().version();

            assertTrue(store.checkAndSet("e1", version, StateInfo.of(Flow.B, null)));
            assertEquals(Flow.B, store.get("e1").orElseThrow().state().state());
        }

        @Test
        void testCheckAndSet_shouldFail_whenVersionStale() {
            store.initialize("e1", StateInfo.of(Flow.A, null));
            final var staleVersion = store.get("e1").orElseThrow().version();
            // someone else advances the record, bumping its version
            store.checkAndSet("e1", staleVersion, StateInfo.of(Flow.B, null));

            // our write with the now-stale version must be rejected
            assertFalse(store.checkAndSet("e1", staleVersion, StateInfo.of(Flow.A, null)));
            assertEquals(Flow.B, store.get("e1").orElseThrow().state().state());
        }
    }

    /** Minimal generation-versioned store: mirrors AerospikeStore's ConcurrentStore<Long> contract. */
    private static final class FakeConcurrentStore implements ConcurrentStore<Item, Long> {
        private record Rec(Item item, long gen) {
        }

        private final Map<String, Rec> data = new ConcurrentHashMap<>();

        @Override
        public Optional<Versioned<Item, Long>> getWithVersion(final String id) {
            return Optional.ofNullable(data.get(id)).map(r -> Versioned.of(r.item(), r.gen()));
        }

        @Override
        public void checkAndUpdate(final Item item, final Long expectedVersion) {
            data.compute(item.id(), (k, rec) -> {
                if (rec == null || rec.gen() != expectedVersion) {
                    throw new ConcurrentUpdateException("version mismatch for " + k);
                }
                return new Rec(item, rec.gen() + 1);
            });
        }

        @Override
        public void create(final Item item) {
            if (data.putIfAbsent(item.id(), new Rec(item, 0L)) != null) {
                throw new IllegalStateException("exists");
            }
        }

        @Override
        public void update(final Item item) {
            data.computeIfPresent(item.id(), (k, rec) -> new Rec(item, rec.gen() + 1));
        }

        @Override
        public void delete(final String id) {
            data.remove(id);
        }

        @Override
        public Optional<Item> get(final String id) {
            return Optional.ofNullable(data.get(id)).map(Rec::item);
        }

        @Override
        public Map<String, Item> get(final List<String> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Item> list() {
            return data.values().stream().map(Rec::item).toList();
        }
    }
}
