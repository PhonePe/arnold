/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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
package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.config.RepositorySyncConfiguration;
import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.arnold.core.model.sync.RepositorySyncResult;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.RepositorySyncProvider;
import com.phonepe.arnold.core.storage.ClusterControlStore;
import com.phonepe.arnold.core.storage.VersionedValue;
import com.phonepe.arnold.models.sync.RepositorySyncStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RepositorySyncServiceImplTest {
    private final InMemoryClusterControlStore store = new InMemoryClusterControlStore();

    @Test
    void recordsPerProviderOutcomeAndSkipsUnavailableProviders() {
        final var github = new FakeProvider(VersionControlProviderType.GITHUB, true,
                () -> RepositorySyncResult.completed(12, 40));
        final var gitlab = new FakeProvider(VersionControlProviderType.GITLAB, false, () -> {
            throw new IllegalStateException("must not run");
        });
        final var service = service(Set.of(github, gitlab));

        service.runOnce();

        final var status = service.status();
        assertTrue(status.enabled());
        assertFalse(status.running());
        final var githubStatus = byProvider(status.providers(), "GITHUB");
        assertEquals("SUCCEEDED", githubStatus.status());
        assertEquals(12, githubStatus.repositories());
        assertEquals(40, githubStatus.changeRequests());
        assertTrue(githubStatus.lastSucceededAt() > 0);
        assertTrue(githubStatus.lastAttemptAt() <= githubStatus.lastSucceededAt());
        assertNull(githubStatus.failure());
        final var gitlabStatus = byProvider(status.providers(), "GITLAB");
        assertFalse(gitlabStatus.available());
        assertEquals("NEVER_SYNCED", gitlabStatus.status());
    }

    @Test
    void keepsLastSuccessWhenALaterRunFailsOrIsRateLimited() {
        final var outcomes = new java.util.ArrayDeque<Supplier<RepositorySyncResult>>();
        outcomes.add(() -> RepositorySyncResult.completed(2, 5));
        outcomes.add(() -> {
            throw new IllegalStateException("GitHub down");
        });
        outcomes.add(() -> new RepositorySyncResult(1, 3, "rate limited"));
        final var service = service(Set.of(new FakeProvider(VersionControlProviderType.GITHUB, true,
                () -> outcomes.poll().get())));

        service.runOnce();
        final var succeededAt = byProvider(service.status().providers(), "GITHUB").lastSucceededAt();
        service.runOnce();
        final var failed = byProvider(service.status().providers(), "GITHUB");
        assertEquals("FAILED", failed.status());
        assertEquals("GitHub down", failed.failure());
        assertEquals(succeededAt, failed.lastSucceededAt());
        assertEquals(2, failed.repositories());

        service.runOnce();
        final var limited = byProvider(service.status().providers(), "GITHUB");
        assertEquals("FAILED", limited.status());
        assertEquals("rate limited", limited.failure());
        assertEquals(1, limited.repositories());
        assertEquals(succeededAt, limited.lastSucceededAt());
    }

    @Test
    void triggerRunsOnTheExecutor() {
        final var executor = mock(ExecutorService.class);
        final var service = new RepositorySyncServiceImpl(RepositorySyncConfiguration.defaults(), Set.of(), store,
                new ObjectMapper(), executor, "node-1");

        assertTrue(service.trigger());
        verify(executor).execute(any(Runnable.class));
    }

    private RepositorySyncServiceImpl service(final Set<RepositorySyncProvider> providers) {
        return new RepositorySyncServiceImpl(RepositorySyncConfiguration.defaults(), providers, store,
                new ObjectMapper(), mock(ExecutorService.class), "node-1");
    }

    private static RepositorySyncStatusResponse byProvider(final java.util.List<RepositorySyncStatusResponse> list,
                                                           final String provider) {
        return list.stream().filter(item -> item.provider().equals(provider)).findFirst().orElseThrow();
    }

    private record FakeProvider(VersionControlProviderType type, boolean available,
                                Supplier<RepositorySyncResult> outcome) implements RepositorySyncProvider {
        @Override
        public RepositorySyncResult synchronize() {
            return outcome.get();
        }
    }

    private static final class InMemoryClusterControlStore implements ClusterControlStore {
        private final Map<String, VersionedValue<ClusterControlRecord>> values = new HashMap<>();

        @Override
        public synchronized Optional<ClusterControlRecord> get(final String id) {
            return Optional.ofNullable(values.get(id)).map(VersionedValue::value);
        }

        @Override
        public synchronized Optional<VersionedValue<ClusterControlRecord>> getVersioned(final String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public synchronized void create(final ClusterControlRecord record) {
            if (values.putIfAbsent(record.id(), new VersionedValue<>(record, 1L)) != null) {
                throw new IllegalStateException("Record already exists");
            }
        }

        @Override
        public synchronized void update(final ClusterControlRecord record, final Object expectedVersion) {
            final var current = values.get(record.id());
            if (current == null || !current.version().equals(expectedVersion)) {
                throw new ConcurrentUpdateException("Version mismatch");
            }
            values.put(record.id(), new VersionedValue<>(record, (Long) current.version() + 1));
        }
    }
}
