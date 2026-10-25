package com.phonepe.arnold.core.model.vcs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.Set;

public record UserChangeRequest(
        String id,
        String userId,
        String mergeRequestId,
        Set<MergeRequestRole> roles,
        boolean active,
        boolean open,
        long firstSeenAt,
        long lastSeenAt,
        long providerUpdatedAt
) implements Id {
    @JsonCreator
    public UserChangeRequest(@JsonProperty("id") final String id,
                             @JsonProperty("userId") final String userId,
                             @JsonProperty("mergeRequestId") final String mergeRequestId,
                             @JsonProperty("roles") final Set<MergeRequestRole> roles,
                             @JsonProperty("active") final boolean active,
                             @JsonProperty("open") final boolean open,
                             @JsonProperty("firstSeenAt") final long firstSeenAt,
                             @JsonProperty("lastSeenAt") final long lastSeenAt,
                             @JsonProperty("providerUpdatedAt") final long providerUpdatedAt) {
        this.id = id;
        this.userId = userId;
        this.mergeRequestId = mergeRequestId;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.active = active;
        this.open = open;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public static String key(final String userId, final String mergeRequestId) {
        return userId + ":" + mergeRequestId;
    }

    public static String userReference(final String userId) {
        return "user:" + userId;
    }

    public static String mergeRequestReference(final String mergeRequestId) {
        return "mr:" + mergeRequestId;
    }
}
