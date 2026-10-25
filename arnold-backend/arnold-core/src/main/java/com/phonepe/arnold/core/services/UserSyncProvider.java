package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

public interface UserSyncProvider {
    VersionControlProviderType type();

    void synchronize(UserSyncState state);
}
