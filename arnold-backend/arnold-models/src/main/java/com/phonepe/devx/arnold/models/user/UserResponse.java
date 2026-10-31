package com.phonepe.devx.arnold.models.user;

import java.util.Map;
import java.util.Set;

public record UserResponse(
        String id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        Map<String, ProviderIdentityResponse> providerIdentities,
        Set<String> roles
) {
}
