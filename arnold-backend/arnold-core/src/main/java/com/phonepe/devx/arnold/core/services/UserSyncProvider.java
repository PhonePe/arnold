package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;

public interface UserSyncProvider {
    VersionControlProviderType type();

    void synchronize(UserSyncState state);
}
