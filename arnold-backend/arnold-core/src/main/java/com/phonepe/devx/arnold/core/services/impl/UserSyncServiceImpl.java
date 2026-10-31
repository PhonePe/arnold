package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.devx.arnold.core.config.UserSyncConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.model.sync.SyncStatus;
import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.UserSyncProvider;
import com.phonepe.devx.arnold.core.services.UserSyncScheduler;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.storage.UserSyncStateStore;
import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

@Slf4j
@Singleton
public class UserSyncServiceImpl implements UserSyncService {
    private static final int MAX_STATE_UPDATE_ATTEMPTS = 8;
    private static final long MAX_JITTER_MS = 60_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSyncConfiguration configuration;
    private final UserSyncScheduler scheduler;
    private final UserSyncStateStore stateStore;
    private final EnumMap<VersionControlProviderType, UserSyncProvider> providers;
    private final ExecutorService asyncTaskExecutor;
    private final String nodeId;

    @Inject
    public UserSyncServiceImpl(final @Config UserSyncConfiguration configuration,
                               final UserSyncScheduler scheduler,
                               final UserSyncStateStore stateStore,
                               final Set<UserSyncProvider> providers,
                               final @Named(BindingNames.ASYNC_TASK_EXECUTOR) ExecutorService asyncTaskExecutor,
                               final ClusterNodeIdentity nodeIdentity) {
        this.configuration = configuration;
        this.scheduler = scheduler;
        this.stateStore = stateStore;
        this.providers = index(providers);
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.nodeId = nodeIdentity.id();
    }

    @Override
    public UserSyncStatusResponse ensureScheduled(final VersionControlProviderType provider, final String userId) {
        requireProvider(provider);
        final var state = scheduler.ensureScheduled(provider, userId);
        if (state.lastSucceededAt() == 0 && state.status() != SyncStatus.RUNNING) {
            dispatchOnDemand(state);
        }
        return response(state);
    }

    @Override
    public UserSyncStatusResponse retry(final VersionControlProviderType provider, final String userId) {
        requireProvider(provider);
        final var state = scheduler.retry(provider, userId);
        if (state.status() != SyncStatus.RUNNING) {
            dispatchOnDemand(state);
        }
        return response(state);
    }

    @Override
    public UserSyncStatusResponse status(final VersionControlProviderType provider, final String userId) {
        requireProvider(provider);
        return response(stateStore.get(UserSyncState.key(provider, userId))
                .orElseGet(() -> UserSyncState.neverSynced(provider, userId)));
    }

    @Override
    public void cancel(final VersionControlProviderType provider, final String userId) {
        requireProvider(provider);
        scheduler.cancel(provider, userId);
    }

    @Override
    public void process(final UserSyncState dueState, final String workerId) {
        final var claimed = claim(dueState.id(), workerId);
        if (claimed == null) {
            return;
        }
        try {
            requireProvider(claimed.provider()).synchronize(claimed);
            complete(claimed.id());
        } catch (RuntimeException failure) {
            log.warn("User sync failed for provider:{} userId:{}", claimed.provider(), claimed.userId(), failure);
            fail(claimed.id());
        }
    }

    private void dispatchOnDemand(final UserSyncState state) {
        asyncTaskExecutor.execute(() -> process(state, nodeId + ":on-demand"));
    }

    private UserSyncState claim(final String id, final String workerId) {
        final var current = stateStore.getVersioned(id).orElse(null);
        if (current == null || current.value().nextDueAt() > System.currentTimeMillis()
                || current.value().status() == SyncStatus.RUNNING
                && current.value().leaseUntil() > System.currentTimeMillis()) {
            return null;
        }
        final var now = System.currentTimeMillis();
        final var running = new UserSyncState(id, current.value().userId(), current.value().provider(),
                SyncStatus.RUNNING, now, current.value().lastSucceededAt(), current.value().nextDueAt(),
                current.value().watermark(), workerId, now + configuration.leaseMs(), null,
                current.value().consecutiveFailures());
        try {
            stateStore.update(running, current.version());
            return running;
        } catch (ConcurrentUpdateException conflict) {
            return null;
        }
    }

    private void complete(final String id) {
        updateState(id, current -> {
            final var now = System.currentTimeMillis();
            final var jitter = RANDOM.nextLong(MAX_JITTER_MS + 1);
            return new UserSyncState(id, current.userId(), current.provider(), SyncStatus.SUCCEEDED,
                    current.lastAttemptAt(), now, now + configuration.intervalMs() + jitter, now, null, 0,
                    null, 0);
        });
    }

    private void fail(final String id) {
        updateState(id, current -> {
            final var failures = current.consecutiveFailures() + 1;
            final var exponent = Math.min(4, Math.max(0, failures - 1));
            final var backoff = Math.min(configuration.intervalMs(), 60_000L * (1L << exponent));
            return new UserSyncState(id, current.userId(), current.provider(), SyncStatus.FAILED,
                    current.lastAttemptAt(), current.lastSucceededAt(), System.currentTimeMillis() + backoff,
                    current.watermark(), null, 0, "Sync failed", failures);
        });
    }

    private void updateState(final String id, final UnaryOperator<UserSyncState> update) {
        for (var attempt = 0; attempt < MAX_STATE_UPDATE_ATTEMPTS; attempt++) {
            final var current = stateStore.getVersioned(id).orElseThrow();
            try {
                stateStore.update(update.apply(current.value()), current.version());
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Another worker changed the lease or completion state; retry against the latest generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private UserSyncProvider requireProvider(final VersionControlProviderType provider) {
        final var syncProvider = providers.get(provider);
        if (syncProvider == null) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Unsupported version control provider").build();
        }
        return syncProvider;
    }

    private EnumMap<VersionControlProviderType, UserSyncProvider> index(final Set<UserSyncProvider> configured) {
        final var indexed = new EnumMap<VersionControlProviderType, UserSyncProvider>(VersionControlProviderType.class);
        configured.forEach(provider -> {
            if (indexed.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Multiple user sync providers configured for " + provider.type());
            }
        });
        return indexed;
    }

    private UserSyncStatusResponse response(final UserSyncState state) {
        return new UserSyncStatusResponse(state.provider().name(), state.status().name(), state.lastAttemptAt(),
                state.lastSucceededAt(), state.nextDueAt(), state.failure(), state.status() == SyncStatus.FAILED);
    }
}
