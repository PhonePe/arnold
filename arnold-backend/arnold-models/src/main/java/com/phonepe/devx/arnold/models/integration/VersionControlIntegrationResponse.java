package com.phonepe.devx.arnold.models.integration;

import java.util.Set;

public record VersionControlIntegrationResponse(
        boolean connected,
        String provider,
        String credentialType,
        String providerUserId,
        String providerUsername,
        Set<String> scopes,
        Long expiresAt,
        String status
) {
    public static VersionControlIntegrationResponse disconnected(final String provider) {
        return new VersionControlIntegrationResponse(false, provider, null, null, null, Set.of(), null, "disconnected");
    }
}
