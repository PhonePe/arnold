package com.phonepe.arnold.core.storage;

import com.phonepe.arnold.core.model.sync.UserSyncState;

import java.util.List;
import java.util.Optional;

public interface UserSyncStateStore {
    Optional<UserSyncState> get(String id);

    Optional<VersionedValue<UserSyncState>> getVersioned(String id);

    void create(UserSyncState state);

    void update(UserSyncState state, Object expectedVersion);

    void delete(String id);

    List<UserSyncState> due(long now, int limit);
}
