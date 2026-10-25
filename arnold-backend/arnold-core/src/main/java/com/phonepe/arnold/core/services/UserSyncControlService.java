package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.sync.UserSyncControlState;
import com.phonepe.arnold.core.model.sync.UserSyncMode;

public interface UserSyncControlService {
    String LEADER_ELECTION_NAME = "user-sync";

    UserSyncControlState status();

    UserSyncControlState setMode(UserSyncMode mode);

    void callbackReceived();

    UserSyncControlState resetCallbackObservation();

    boolean periodicPollingEnabled();
}
