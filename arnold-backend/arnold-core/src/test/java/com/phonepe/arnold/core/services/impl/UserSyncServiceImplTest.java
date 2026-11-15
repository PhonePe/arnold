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

package com.phonepe.arnold.core.services.impl;

import com.google.common.util.concurrent.MoreExecutors;
import com.phonepe.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.arnold.core.config.UserSyncConfiguration;
import com.phonepe.arnold.core.model.sync.SyncStatus;
import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.UserSyncProvider;
import com.phonepe.arnold.core.services.UserSyncScheduler;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import com.phonepe.arnold.core.storage.VersionedValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSyncServiceImplTest {
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = MoreExecutors.newDirectExecutorService();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void testEnsureScheduled_shouldDispatchToConfiguredProvider() {
        final var store = new InMemoryUserSyncStateStore();
        final var scheduler = new UserSyncScheduler(store);
        final var provider = mock(UserSyncProvider.class);
        when(provider.type()).thenReturn(VersionControlProviderType.GITHUB);
        final var service = new UserSyncServiceImpl(new UserSyncConfiguration(60_000, 30_000, 10, 2, 60_000),
                scheduler, store, Set.of(provider), executor, new ClusterNodeIdentity());

        final var response = service.ensureScheduled(VersionControlProviderType.GITHUB, "user-1");

        verify(provider).synchronize(any(UserSyncState.class));
        assertEquals("QUEUED", response.status());
        assertEquals(SyncStatus.SUCCEEDED,
                store.get(UserSyncState.key(VersionControlProviderType.GITHUB, "user-1")).orElseThrow().status());
    }

    private static class InMemoryUserSyncStateStore implements UserSyncStateStore {
        private UserSyncState value;
        private long version;

        @Override
        public Optional<UserSyncState> get(final String id) {
            return Optional.ofNullable(value);
        }

        @Override
        public Optional<VersionedValue<UserSyncState>> getVersioned(final String id) {
            return Optional.ofNullable(value).map(item -> new VersionedValue<>(item, version));
        }

        @Override
        public void create(final UserSyncState state) {
            value = state;
            version++;
        }

        @Override
        public void update(final UserSyncState state, final Object expectedVersion) {
            value = state;
            version++;
        }

        @Override
        public void delete(final String id) {
            value = null;
        }

        @Override
        public List<UserSyncState> due(final long now, final int limit) {
            return List.of();
        }
    }
}
