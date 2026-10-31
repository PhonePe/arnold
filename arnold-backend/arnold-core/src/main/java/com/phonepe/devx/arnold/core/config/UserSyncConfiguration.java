package com.phonepe.devx.arnold.core.config;

public record UserSyncConfiguration(
        long intervalMs,
        long leaseMs,
        int workerBatchSize,
        int maxConcurrentUsers,
        long leaderLeaseMs
) {
    private static final long MINUTE_MS = 60_000L;

    public UserSyncConfiguration {
        intervalMs = intervalMs > 0 ? intervalMs : 15 * MINUTE_MS;
        leaseMs = leaseMs > 0 ? leaseMs : 5 * MINUTE_MS;
        workerBatchSize = workerBatchSize > 0 ? workerBatchSize : 25;
        maxConcurrentUsers = maxConcurrentUsers > 0 ? maxConcurrentUsers : 4;
        leaderLeaseMs = leaderLeaseMs > 0 ? leaderLeaseMs : 2 * MINUTE_MS;
    }
}
