package com.phonepe.arnold.core.services;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.sync.SyncStatus;
import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.storage.UserSyncStateStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserSyncScheduler {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

    private final UserSyncStateStore store;

    @Inject
    public UserSyncScheduler(final UserSyncStateStore store) {
        this.store = store;
    }

    public UserSyncState ensureScheduled(final VersionControlProviderType provider, final String userId) {
        final var id = UserSyncState.key(provider, userId);
        final var current = store.get(id);
        if (current.isPresent()) {
            return current.get();
        }
        final var state = new UserSyncState(id, userId, provider, SyncStatus.QUEUED,
                0, 0, System.currentTimeMillis(), 0, null, 0, null, 0);
        try {
            store.create(state);
            return state;
        } catch (RuntimeException race) {
            return store.get(id).orElseThrow(() -> race);
        }
    }

    public UserSyncState retry(final VersionControlProviderType provider, final String userId) {
        final var id = UserSyncState.key(provider, userId);
        ensureScheduled(provider, userId);
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(id).orElseThrow();
            if (current.value().status() == SyncStatus.RUNNING
                    && current.value().leaseUntil() > System.currentTimeMillis()) {
                return current.value();
            }
            final var queued = new UserSyncState(id, userId, provider, SyncStatus.QUEUED,
                    current.value().lastAttemptAt(), current.value().lastSucceededAt(), System.currentTimeMillis(),
                    current.value().watermark(), null, 0, null, current.value().consecutiveFailures());
            try {
                store.update(queued, current.version());
                return queued;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read and retry.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    public void cancel(final VersionControlProviderType provider, final String userId) {
        store.delete(UserSyncState.key(provider, userId));
    }
}
