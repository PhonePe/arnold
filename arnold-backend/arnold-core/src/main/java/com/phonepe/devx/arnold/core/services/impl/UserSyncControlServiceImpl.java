package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.devx.arnold.core.model.sync.UserSyncControlState;
import com.phonepe.devx.arnold.core.model.sync.UserSyncMode;
import com.phonepe.devx.arnold.core.services.LeaderElectionService;
import com.phonepe.devx.arnold.core.services.UserSyncControlService;
import com.phonepe.devx.arnold.core.storage.ClusterControlStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserSyncControlServiceImpl implements UserSyncControlService {
    private static final int MAX_ATTEMPTS = 8;
    private static final String MODE_SETTING = "user-sync-mode";
    private static final String CALLBACK_SETTING = "user-sync-callback-observed";

    private final ClusterControlStore store;
    private final LeaderElectionService leaderElectionService;
    private final ClusterNodeIdentity nodeIdentity;

    @Inject
    public UserSyncControlServiceImpl(final ClusterControlStore store,
                                      final LeaderElectionService leaderElectionService,
                                      final ClusterNodeIdentity nodeIdentity) {
        this.store = store;
        this.leaderElectionService = leaderElectionService;
        this.nodeIdentity = nodeIdentity;
        initializeMode();
    }

    @Override
    public UserSyncControlState status() {
        final var configuredMode = mode();
        final var callback = callbackRecord();
        final var callbackObserved = callback != null && Boolean.parseBoolean(callback.value());
        final var effectiveMode = configuredMode == UserSyncMode.AUTO ? UserSyncMode.POLLING : configuredMode;
        final var leadership = leaderElectionService.current(LEADER_ELECTION_NAME);
        return new UserSyncControlState(configuredMode, effectiveMode, callbackObserved,
                callback == null ? 0 : callback.updatedAt(), effectiveMode == UserSyncMode.POLLING,
                leadership.ownerId(), leadership.leaseUntil(),
                nodeIdentity.id().equals(leadership.ownerId()), nodeIdentity.id());
    }

    @Override
    public UserSyncControlState setMode(final UserSyncMode mode) {
        upsertSetting(MODE_SETTING, mode.name());
        return status();
    }

    @Override
    public void callbackReceived() {
        upsertSetting(CALLBACK_SETTING, Boolean.TRUE.toString());
    }

    @Override
    public UserSyncControlState resetCallbackObservation() {
        upsertSetting(CALLBACK_SETTING, Boolean.FALSE.toString());
        return status();
    }

    @Override
    public boolean periodicPollingEnabled() {
        return mode() != UserSyncMode.CALLBACK_ONLY;
    }

    private void initializeMode() {
        final var id = ClusterControlRecord.settingId(MODE_SETTING);
        if (store.get(id).isPresent()) {
            return;
        }
        try {
            store.create(setting(id, UserSyncMode.AUTO.name()));
        } catch (RuntimeException race) {
            if (store.get(id).isEmpty()) {
                throw race;
            }
        }
    }

    private UserSyncMode mode() {
        return store.get(ClusterControlRecord.settingId(MODE_SETTING))
                .map(ClusterControlRecord::value)
                .map(UserSyncMode::valueOf)
                .orElse(UserSyncMode.AUTO);
    }

    private ClusterControlRecord callbackRecord() {
        return store.get(ClusterControlRecord.settingId(CALLBACK_SETTING)).orElse(null);
    }

    private void upsertSetting(final String name, final String value) {
        final var id = ClusterControlRecord.settingId(name);
        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(id);
            if (current.isEmpty()) {
                try {
                    store.create(setting(id, value));
                    return;
                } catch (RuntimeException race) {
                    continue;
                }
            }
            try {
                store.update(setting(id, value), current.get().version());
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the setting.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private ClusterControlRecord setting(final String id, final String value) {
        return new ClusterControlRecord(id, "SETTING", value, null, 0, System.currentTimeMillis());
    }
}
