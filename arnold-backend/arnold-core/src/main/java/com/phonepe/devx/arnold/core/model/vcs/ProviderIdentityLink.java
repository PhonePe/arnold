package com.phonepe.devx.arnold.core.model.vcs;

import com.livetheoogway.crudstore.core.Id;

public record ProviderIdentityLink(
        String id,
        VersionControlProviderType provider,
        String providerUserId,
        String userId,
        long updatedAt
) implements Id {
    public static String key(final VersionControlProviderType provider, final String providerUserId) {
        return provider.name().toLowerCase() + ":" + providerUserId;
    }
}
