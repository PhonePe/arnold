package com.phonepe.arnold.core.model.vcs;

import com.livetheoogway.crudstore.core.Id;

import java.util.Set;

public record VersionControlCredential(
        String id,
        String userId,
        VersionControlProviderType provider,
        String credentialType,
        String accessToken,
        String refreshToken,
        String providerUserId,
        String providerUsername,
        Set<String> scopes,
        Long expiresAt,
        String status,
        long createdAt,
        long updatedAt
) implements Id {
    public VersionControlCredential {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public static String key(final VersionControlProviderType provider, final String userId) {
        return provider.name().toLowerCase() + ":" + userId;
    }
}
