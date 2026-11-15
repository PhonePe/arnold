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

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.arnold.core.model.sync.UserSyncMode;
import com.phonepe.arnold.core.services.LeaderElectionService;
import com.phonepe.arnold.core.storage.ClusterControlStore;
import com.phonepe.arnold.core.storage.VersionedValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterControlServicesTest {
    @Nested
    class LeaderElection {
        @Test
        void testAcquireOrRenew_shouldElectSingleLeader_whenMultipleNodesCompete() {
            final var store = new InMemoryClusterControlStore();
            final var service = new ClusterLeaderElectionServiceImpl(store);

            final var first = service.acquireOrRenew("sync", "node-a", 60_000);
            final var standby = service.acquireOrRenew("sync", "node-b", 60_000);

            assertTrue(first.leader());
            assertEquals("node-a", first.ownerId());
            assertFalse(standby.leader());
            assertEquals("node-a", standby.ownerId());
        }

        @Test
        void testAcquireOrRenew_shouldTransferLeadership_whenLeaseExpires() {
            final var store = new InMemoryClusterControlStore();
            final var service = new ClusterLeaderElectionServiceImpl(store);
            service.acquireOrRenew("sync", "node-a", 60_000);
            store.expire(ClusterControlRecord.leaderId("sync"));

            final var next = service.acquireOrRenew("sync", "node-b", 60_000);

            assertTrue(next.leader());
            assertEquals("node-b", next.ownerId());
        }
    }

    @Nested
    class SyncControl {
        @Test
        void testCallbackReceived_shouldKeepReconciliationEnabled_whenModeIsAuto() {
            final var leaderElection = mock(LeaderElectionService.class);
            when(leaderElection.current(anyString()))
                    .thenReturn(new LeaderElectionService.Leadership(false, null, 0));
            final var service = new UserSyncControlServiceImpl(new InMemoryClusterControlStore(),
                    leaderElection, new ClusterNodeIdentity());

            assertEquals(UserSyncMode.AUTO, service.status().configuredMode());
            assertTrue(service.periodicPollingEnabled());

            service.callbackReceived();

            assertEquals(UserSyncMode.POLLING, service.status().effectiveMode());
            assertTrue(service.status().callbackObserved());
            assertTrue(service.periodicPollingEnabled());
        }

        @Test
        void testSetMode_shouldKeepPollingEnabled_whenCallbackWasObservedButModeIsPolling() {
            final var leaderElection = mock(LeaderElectionService.class);
            when(leaderElection.current(anyString()))
                    .thenReturn(new LeaderElectionService.Leadership(false, null, 0));
            final var service = new UserSyncControlServiceImpl(new InMemoryClusterControlStore(),
                    leaderElection, new ClusterNodeIdentity());
            service.callbackReceived();

            final var status = service.setMode(UserSyncMode.POLLING);

            assertEquals(UserSyncMode.POLLING, status.effectiveMode());
            assertTrue(service.periodicPollingEnabled());
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

        private synchronized void expire(final String id) {
            final var current = values.get(id);
            final var value = current.value();
            values.put(id, new VersionedValue<>(new ClusterControlRecord(value.id(), value.kind(), value.value(),
                    value.ownerId(), 0, System.currentTimeMillis()), (Long) current.version() + 1));
        }
    }
}
