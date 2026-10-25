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

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.arnold.core.services.LeaderElectionService;
import com.phonepe.arnold.core.storage.ClusterControlStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClusterLeaderElectionServiceImpl implements LeaderElectionService {
    private static final int MAX_ATTEMPTS = 8;

    private final ClusterControlStore store;

    @Inject
    public ClusterLeaderElectionServiceImpl(final ClusterControlStore store) {
        this.store = store;
    }

    @Override
    public Leadership acquireOrRenew(final String electionName,
                                     final String contenderId,
                                     final long leaseMs) {
        final var id = ClusterControlRecord.leaderId(electionName);
        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final var now = System.currentTimeMillis();
            final var current = store.getVersioned(id);
            if (current.isEmpty()) {
                final var created = lease(id, contenderId, now, leaseMs);
                try {
                    store.create(created);
                    return leadership(created, contenderId, now);
                } catch (RuntimeException race) {
                    continue;
                }
            }
            final var value = current.get().value();
            if (value.leaseUntil() > now && !contenderId.equals(value.ownerId())) {
                return leadership(value, contenderId, now);
            }
            final var renewed = lease(id, contenderId, now, leaseMs);
            try {
                store.update(renewed, current.get().version());
                return leadership(renewed, contenderId, now);
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the lease.
            }
        }
        return current(electionName);
    }

    @Override
    public Leadership current(final String electionName) {
        final var now = System.currentTimeMillis();
        return store.get(ClusterControlRecord.leaderId(electionName))
                .map(value -> leadership(value, null, now))
                .orElseGet(() -> new Leadership(false, null, 0));
    }

    @Override
    public void release(final String electionName, final String contenderId) {
        final var id = ClusterControlRecord.leaderId(electionName);
        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(id);
            if (current.isEmpty() || !contenderId.equals(current.get().value().ownerId())) {
                return;
            }
            final var released = new ClusterControlRecord(id, "LEASE", electionName, null, 0,
                    System.currentTimeMillis());
            try {
                store.update(released, current.get().version());
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the lease.
            }
        }
    }

    private ClusterControlRecord lease(final String id,
                                       final String contenderId,
                                       final long now,
                                       final long leaseMs) {
        return new ClusterControlRecord(id, "LEASE", null, contenderId, now + leaseMs, now);
    }

    private Leadership leadership(final ClusterControlRecord record,
                                  final String contenderId,
                                  final long now) {
        final var active = record.leaseUntil() > now;
        return new Leadership(active && contenderId != null && contenderId.equals(record.ownerId()),
                active ? record.ownerId() : null, active ? record.leaseUntil() : 0);
    }
}
