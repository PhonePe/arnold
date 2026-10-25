package com.phonepe.arnold.models.sync;

public record UserSyncStatusResponse(
        String provider,
        String status,
        long lastAttemptAt,
        long lastSucceededAt,
        long nextDueAt,
        String failure,
        boolean retryable
) {
}
