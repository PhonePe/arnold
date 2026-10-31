package com.phonepe.devx.arnold.core.model.sync;

public record UserSyncControlState(
        UserSyncMode configuredMode,
        UserSyncMode effectiveMode,
        boolean callbackObserved,
        long lastCallbackAt,
        boolean pollingEnabled,
        String leaderId,
        long leaderLeaseUntil,
        boolean currentNodeLeader,
        String currentNodeId
) {
}
