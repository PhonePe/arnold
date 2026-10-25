package com.phonepe.arnold.core.model.sync;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

public record UserSyncState(
        String id,
        String userId,
        VersionControlProviderType provider,
        SyncStatus status,
        long lastAttemptAt,
        long lastSucceededAt,
        long nextDueAt,
        long watermark,
        String leaseOwner,
        long leaseUntil,
        String failure,
        int consecutiveFailures
) implements Id {
    @JsonCreator
    public UserSyncState(@JsonProperty("id") final String id,
                         @JsonProperty("userId") final String userId,
                         @JsonProperty("provider") final VersionControlProviderType provider,
                         @JsonProperty("status") final SyncStatus status,
                         @JsonProperty("lastAttemptAt") final long lastAttemptAt,
                         @JsonProperty("lastSucceededAt") final long lastSucceededAt,
                         @JsonProperty("nextDueAt") final long nextDueAt,
                         @JsonProperty("watermark") final long watermark,
                         @JsonProperty("leaseOwner") final String leaseOwner,
                         @JsonProperty("leaseUntil") final long leaseUntil,
                         @JsonProperty("failure") final String failure,
                         @JsonProperty("consecutiveFailures") final int consecutiveFailures) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.status = status == null ? SyncStatus.NEVER_SYNCED : status;
        this.lastAttemptAt = lastAttemptAt;
        this.lastSucceededAt = lastSucceededAt;
        this.nextDueAt = nextDueAt;
        this.watermark = watermark;
        this.leaseOwner = leaseOwner;
        this.leaseUntil = leaseUntil;
        this.failure = failure;
        this.consecutiveFailures = consecutiveFailures;
    }

    public static String key(final VersionControlProviderType provider, final String userId) {
        return provider.name().toLowerCase() + ":" + userId;
    }

    public static UserSyncState neverSynced(final VersionControlProviderType provider, final String userId) {
        return new UserSyncState(key(provider, userId), userId, provider, SyncStatus.NEVER_SYNCED,
                0, 0, 0, 0, null, 0, null, 0);
    }
}
