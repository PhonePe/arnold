package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;

public interface UserSyncService {
    UserSyncStatusResponse ensureScheduled(String userId);

    UserSyncStatusResponse retry(String userId);

    UserSyncStatusResponse status(String userId);

    void cancel(String userId);

    void process(UserSyncState dueState, String workerId);
}
