package com.phonepe.arnold.core.config;

import javax.validation.constraints.NotBlank;

public record GitLabSyncConfiguration(
        @NotBlank String webhookSecret,
        long historyWindowMs,
        long watermarkOverlapMs
) {
    private static final long MINUTE_MS = 60_000L;

    public GitLabSyncConfiguration {
        historyWindowMs = historyWindowMs > 0 ? historyWindowMs : 90L * 24 * 60 * MINUTE_MS;
        watermarkOverlapMs = watermarkOverlapMs > 0 ? watermarkOverlapMs : 5 * MINUTE_MS;
    }

    public GitLabSyncConfiguration(final String webhookSecret) {
        this(webhookSecret, 90L * 24 * 60 * MINUTE_MS, 5 * MINUTE_MS);
    }
}
