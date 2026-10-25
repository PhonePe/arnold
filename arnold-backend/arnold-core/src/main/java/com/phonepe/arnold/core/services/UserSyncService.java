package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.sync.UserSyncStatusResponse;

public interface UserSyncService {
    UserSyncStatusResponse ensureScheduled(VersionControlProviderType provider, String userId);

    UserSyncStatusResponse retry(VersionControlProviderType provider, String userId);

    UserSyncStatusResponse status(VersionControlProviderType provider, String userId);

    void cancel(VersionControlProviderType provider, String userId);

    void process(UserSyncState dueState, String workerId);
}
