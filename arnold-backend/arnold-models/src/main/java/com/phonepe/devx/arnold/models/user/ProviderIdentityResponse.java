package com.phonepe.devx.arnold.models.user;

public record ProviderIdentityResponse(
        String providerUserId,
        String username,
        String avatarUrl
) {
}
