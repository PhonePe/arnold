package com.phonepe.devx.arnold.models.admin;

public record UserSyncControlResponse(
        String configuredMode,
        String effectiveMode,
        boolean callbackObserved,
        long lastCallbackAt,
        boolean pollingEnabled,
        String leaderId,
        long leaderLeaseUntil,
        boolean currentNodeLeader,
        String currentNodeId
) {
}
