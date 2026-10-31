package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.sync.UserSyncControlState;
import com.phonepe.devx.arnold.core.model.sync.UserSyncMode;

public interface UserSyncControlService {
    String LEADER_ELECTION_NAME = "user-sync";

    UserSyncControlState status();

    UserSyncControlState setMode(UserSyncMode mode);

    void callbackReceived();

    UserSyncControlState resetCallbackObservation();

    boolean periodicPollingEnabled();
}
