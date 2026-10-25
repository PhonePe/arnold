package com.phonepe.arnold.models.user;

public record ProviderIdentityResponse(
        String providerUserId,
        String username,
        String avatarUrl
) {
}
