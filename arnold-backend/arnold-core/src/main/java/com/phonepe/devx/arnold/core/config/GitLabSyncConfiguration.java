package com.phonepe.devx.arnold.core.config;

import javax.validation.constraints.NotBlank;

public record GitLabSyncConfiguration(
        @NotBlank String webhookSecret,
        long intervalMs,
        long leaseMs,
        long historyWindowMs,
        long watermarkOverlapMs,
        int workerBatchSize,
        int maxConcurrentUsers,
        long leaderLeaseMs
) {
    private static final long MINUTE_MS = 60_000L;

    public GitLabSyncConfiguration {
        intervalMs = intervalMs > 0 ? intervalMs : 15 * MINUTE_MS;
        leaseMs = leaseMs > 0 ? leaseMs : 5 * MINUTE_MS;
        historyWindowMs = historyWindowMs > 0 ? historyWindowMs : 90L * 24 * 60 * MINUTE_MS;
        watermarkOverlapMs = watermarkOverlapMs > 0 ? watermarkOverlapMs : 5 * MINUTE_MS;
        workerBatchSize = workerBatchSize > 0 ? workerBatchSize : 25;
        maxConcurrentUsers = maxConcurrentUsers > 0 ? maxConcurrentUsers : 4;
        leaderLeaseMs = leaderLeaseMs > 0 ? leaderLeaseMs : 120_000L;
    }

    public GitLabSyncConfiguration(final String webhookSecret) {
        this(webhookSecret, 15 * MINUTE_MS, 5 * MINUTE_MS, 90L * 24 * 60 * MINUTE_MS,
                5 * MINUTE_MS, 25, 4, 120_000L);
    }

    public GitLabSyncConfiguration(final String webhookSecret,
                                   final long intervalMs,
                                   final long leaseMs,
                                   final long historyWindowMs,
                                   final long watermarkOverlapMs,
                                   final int workerBatchSize,
                                   final int maxConcurrentUsers) {
        this(webhookSecret, intervalMs, leaseMs, historyWindowMs, watermarkOverlapMs, workerBatchSize,
                maxConcurrentUsers, 120_000L);
    }
}
