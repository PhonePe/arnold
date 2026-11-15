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

package com.phonepe.arnold.core.provider;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthCredentialManagerTest {
    private static final long NOW = 1_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    private static final String ID = "github:u1";

    private final VersionedStore store = new VersionedStore();

    @Test
    void testValid_shouldRefreshOnce_whenConcurrentRequestsSeeAnExpiringToken() throws Exception {
        store.create(credential("old", "r1", NOW, "connected"));
        final var refreshes = new AtomicInteger();
        final var manager = manager(refreshToken -> {
            refreshes.incrementAndGet();
            sleep(50);
            return new OAuthCredentialManager.Token("new", "r2", NOW + 3_600_000, Set.of());
        });
        final var executor = Executors.newFixedThreadPool(8);
        final var start = new CountDownLatch(1);
        try {
            final var results = new ArrayList<Future<VersionControlCredential>>();
            for (var i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return manager.valid("u1");
                }));
            }
            start.countDown();
            for (final var result : results) {
                assertEquals("new", result.get().accessToken());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, refreshes.get());
        assertEquals("r2", store.get(ID).orElseThrow().refreshToken());
    }

    @Test
    void testValid_shouldAdoptNewerCredential_whenAnotherNodeRefreshedConcurrently() {
        store.create(credential("old", "r1", NOW, "connected"));
        final var manager = manager(refreshToken -> {
            // Another node completes its refresh while this one is still talking to the provider.
            store.update(credential("other-node", "r-other", NOW + 3_600_000, "connected"));
            return new OAuthCredentialManager.Token("mine", "r-mine", NOW + 3_600_000, Set.of());
        });

        assertEquals("other-node", manager.valid("u1").accessToken());
        assertEquals("other-node", store.get(ID).orElseThrow().accessToken());
    }

    @Test
    void testValid_shouldMarkReconnectRequired_whenRefreshTokenIsRejected() {
        store.create(credential("old", "r1", NOW, "connected"));
        final var refreshes = new AtomicInteger();
        final var manager = manager(refreshToken -> {
            refreshes.incrementAndGet();
            throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).context(Map.of("httpStatus", 400))
                    .message("GitLab request failed with HTTP 400: {\"error\":\"invalid_grant\"}").build();
        });

        final var error = assertThrows(ArnoldError.class, () -> manager.valid("u1"));

        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
        assertEquals(Map.of("reason", "SCM_RECONNECT_REQUIRED", "provider", "GITHUB", "reconnect", true),
                error.getDetails());
        final var stored = store.get(ID).orElseThrow();
        assertEquals(OAuthCredentialManager.RECONNECT_REQUIRED, stored.status());
        assertEquals("r1", stored.refreshToken());

        final var again = assertThrows(ArnoldError.class, () -> manager.valid("u1"));
        assertEquals(true, again.getDetails().get("reconnect"));
        assertEquals(1, refreshes.get());
    }

    @Test
    void testValid_shouldUseRotatedCredential_whenRefreshFailsBecauseAnotherNodeConsumedTheToken() {
        store.create(credential("old", "r1", NOW, "connected"));
        final var manager = manager(refreshToken -> {
            store.update(credential("other-node", "r-other", NOW + 3_600_000, "connected"));
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).context(Map.of("oauthError", "bad_refresh_token"))
                    .message("GitHub rejected the authorization: bad_refresh_token").build();
        });

        assertEquals("other-node", manager.valid("u1").accessToken());
        assertEquals("connected", store.get(ID).orElseThrow().status());
    }

    @Test
    void testUnauthorized_shouldMarkReconnectRequiredAndRaiseSignal_whenProviderRejectsCurrentToken() {
        final var used = credential("access", "r1", NOW + 3_600_000, "connected");
        store.create(used);
        final var manager = manager(refreshToken -> {
            throw new AssertionError("must not refresh");
        });

        final var error = assertThrows(ArnoldError.class, () -> manager.unauthorized("u1", used));

        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
        assertEquals("GITHUB", error.getDetails().get("provider"));
        assertEquals(OAuthCredentialManager.RECONNECT_REQUIRED, store.get(ID).orElseThrow().status());
    }

    @Test
    void testUnauthorized_shouldReturnNewerCredential_whenTokenWasRotatedMeanwhile() {
        final var used = credential("access", "r1", NOW + 3_600_000, "connected");
        store.create(credential("rotated", "r2", NOW + 3_600_000, "connected"));
        final var manager = manager(refreshToken -> {
            throw new AssertionError("must not refresh");
        });

        assertEquals("rotated", manager.unauthorized("u1", used).accessToken());
        assertEquals("connected", store.get(ID).orElseThrow().status());
    }

    private OAuthCredentialManager manager(final Function<String, OAuthCredentialManager.Token> refresh) {
        return new OAuthCredentialManager(store, VersionControlProviderType.GITHUB, 60_000, refresh, CLOCK);
    }

    private static VersionControlCredential credential(final String access, final String refresh,
                                                       final long expiresAt, final String status) {
        return new VersionControlCredential(ID, "u1", VersionControlProviderType.GITHUB, "GITHUB_APP", access,
                refresh, "9", "alice", Set.of(), expiresAt, status, 1L, 1L);
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class VersionedStore implements ConcurrentStore<VersionControlCredential, Long> {
        private final Map<String, Versioned<VersionControlCredential, Long>> items = new HashMap<>();

        @Override
        public synchronized Optional<Versioned<VersionControlCredential, Long>> getWithVersion(final String id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public synchronized void checkAndUpdate(final VersionControlCredential item, final Long version) {
            final var current = items.get(item.id());
            if (current == null || !current.version().equals(version)) {
                throw new ConcurrentUpdateException("version mismatch");
            }
            items.put(item.id(), Versioned.of(item, version + 1));
        }

        @Override
        public synchronized void create(final VersionControlCredential item) {
            items.put(item.id(), Versioned.of(item, 1L));
        }

        @Override
        public synchronized void update(final VersionControlCredential item) {
            final var current = items.get(item.id());
            items.put(item.id(), Versioned.of(item, current == null ? 1L : current.version() + 1));
        }

        @Override
        public synchronized void delete(final String id) {
            items.remove(id);
        }

        @Override
        public synchronized Optional<VersionControlCredential> get(final String id) {
            return getWithVersion(id).map(Versioned::value);
        }

        @Override
        public Map<String, VersionControlCredential> get(final List<String> ids) {
            final var result = new HashMap<String, VersionControlCredential>();
            ids.forEach(id -> get(id).ifPresent(value -> result.put(id, value)));
            return result;
        }

        @Override
        public synchronized List<VersionControlCredential> list() {
            return items.values().stream().map(Versioned::value).toList();
        }
    }
}
