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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.arnold.core.config.RepositorySyncConfiguration;
import com.phonepe.arnold.core.guice.bindings.BindingNames;
import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.arnold.core.model.sync.RepositorySyncState;
import com.phonepe.arnold.core.model.sync.SyncStatus;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.RepositorySyncProvider;
import com.phonepe.arnold.core.services.RepositorySyncService;
import com.phonepe.arnold.core.storage.ClusterControlStore;
import com.phonepe.arnold.models.sync.RepositorySyncOverviewResponse;
import com.phonepe.arnold.models.sync.RepositorySyncStatusResponse;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/**
 * Runs every available {@link RepositorySyncProvider} in turn and records per-provider outcomes in the cluster
 * control set (one record per provider, see {@link RepositorySyncState#key}).
 */
@Slf4j
@Singleton
public class RepositorySyncServiceImpl implements RepositorySyncService {
    private static final String KIND = "REPOSITORY_SYNC";
    private static final int MAX_STATE_UPDATE_ATTEMPTS = 8;
    private static final int MAX_FAILURE_LENGTH = 300;

    private final RepositorySyncConfiguration configuration;
    private final List<RepositorySyncProvider> providers;
    private final ClusterControlStore store;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final String nodeId;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    public RepositorySyncServiceImpl(final ArnoldCoreConfiguration configuration,
                                     final Set<RepositorySyncProvider> providers,
                                     final ClusterControlStore store,
                                     final ObjectMapper mapper,
                                     final @Named(BindingNames.ASYNC_TASK_EXECUTOR) ExecutorService executor,
                                     final ClusterNodeIdentity nodeIdentity) {
        this(configuration.getRepositorySync(), providers, store, mapper, executor, nodeIdentity.id());
    }

    RepositorySyncServiceImpl(final RepositorySyncConfiguration configuration,
                              final Set<RepositorySyncProvider> providers,
                              final ClusterControlStore store,
                              final ObjectMapper mapper,
                              final ExecutorService executor,
                              final String nodeId) {
        this.configuration = configuration == null ? RepositorySyncConfiguration.defaults() : configuration;
        this.providers = providers.stream().sorted(Comparator.comparing(RepositorySyncProvider::type)).toList();
        this.store = store;
        this.mapper = mapper;
        this.executor = executor;
        this.nodeId = nodeId;
    }

    @Override
    public RepositorySyncOverviewResponse status() {
        final var statuses = providers.stream().map(provider -> {
            final var state = state(provider.type());
            return new RepositorySyncStatusResponse(provider.type().name(), provider.available(),
                    state.map(value -> value.status().name()).orElse(SyncStatus.NEVER_SYNCED.name()),
                    state.map(RepositorySyncState::lastAttemptAt).orElse(0L),
                    state.map(RepositorySyncState::lastSucceededAt).orElse(0L),
                    state.map(RepositorySyncState::repositories).orElse(0),
                    state.map(RepositorySyncState::changeRequests).orElse(0),
                    state.map(RepositorySyncState::failure).orElse(null));
        }).toList();
        return new RepositorySyncOverviewResponse(configuration.enabled(), running.get()
                || statuses.stream().anyMatch(status -> SyncStatus.RUNNING.name().equals(status.status())),
                statuses);
    }

    @Override
    public boolean trigger() {
        if (running.get()) {
            return false;
        }
        executor.execute(this::runOnce);
        return true;
    }

    @Override
    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            providers.stream().filter(RepositorySyncProvider::available).forEach(this::run);
        } finally {
            running.set(false);
        }
    }

    private void run(final RepositorySyncProvider provider) {
        final var type = provider.type();
        final var startedAt = System.currentTimeMillis();
        record(type, current -> new RepositorySyncState(type, SyncStatus.RUNNING, startedAt,
                current.lastSucceededAt(), current.repositories(), current.changeRequests(), null));
        try {
            final var result = provider.synchronize();
            final var finishedAt = System.currentTimeMillis();
            log.info("Repository sync for {} imported {} change requests from {} repositories in {}ms{}", type,
                    result.changeRequests(), result.repositories(), finishedAt - startedAt,
                    result.failure() == null ? "" : " (" + result.failure() + ")");
            record(type, current -> new RepositorySyncState(type,
                    result.failure() == null ? SyncStatus.SUCCEEDED : SyncStatus.FAILED, startedAt,
                    result.failure() == null ? finishedAt : current.lastSucceededAt(), result.repositories(),
                    result.changeRequests(), result.failure()));
        } catch (RuntimeException failure) {
            log.warn("Repository sync failed for {}", type, failure);
            final var message = failure.getMessage() == null ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            record(type, current -> new RepositorySyncState(type, SyncStatus.FAILED, startedAt,
                    current.lastSucceededAt(), current.repositories(), current.changeRequests(),
                    message.substring(0, Math.min(message.length(), MAX_FAILURE_LENGTH))));
        }
    }

    private Optional<RepositorySyncState> state(final VersionControlProviderType type) {
        return store.get(RepositorySyncState.key(type)).map(record -> read(record.value()));
    }

    private void record(final VersionControlProviderType type, final UnaryOperator<RepositorySyncState> update) {
        final var id = RepositorySyncState.key(type);
        try {
            for (var attempt = 0; attempt < MAX_STATE_UPDATE_ATTEMPTS; attempt++) {
                final var current = store.getVersioned(id);
                final var previous = current.map(value -> read(value.value().value()))
                        .orElseGet(() -> new RepositorySyncState(type, SyncStatus.NEVER_SYNCED, 0, 0, 0, 0, null));
                final var next = new ClusterControlRecord(id, KIND, write(update.apply(previous)), nodeId, 0,
                        System.currentTimeMillis());
                try {
                    if (current.isPresent()) {
                        store.update(next, current.get().version());
                    } else {
                        store.create(next);
                    }
                    return;
                } catch (ConcurrentUpdateException conflict) {
                    // Another node recorded a status concurrently; retry against the latest generation.
                } catch (RuntimeException createRace) {
                    if (current.isPresent()) {
                        throw createRace;
                    }
                }
            }
            log.warn("Gave up recording repository sync status for {} after concurrent updates", type);
        } catch (RuntimeException failure) {
            log.warn("Could not record repository sync status for {}", type, failure);
        }
    }

    private RepositorySyncState read(final String value) {
        try {
            return mapper.readValue(value, RepositorySyncState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable repository sync state", e);
        }
    }

    private String write(final RepositorySyncState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize repository sync state", e);
        }
    }
}
