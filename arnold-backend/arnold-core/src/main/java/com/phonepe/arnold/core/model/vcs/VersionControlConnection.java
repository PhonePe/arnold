package com.phonepe.arnold.core.model.vcs;

import java.util.Set;

public record VersionControlConnection(
        String credentialType,
        String secretReference,
        String providerUserId,
        String providerUsername,
        Set<String> scopes,
        Long expiresAt
) {
    public VersionControlConnection {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
