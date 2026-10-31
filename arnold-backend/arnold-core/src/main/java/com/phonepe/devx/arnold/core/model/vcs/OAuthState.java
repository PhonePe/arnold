package com.phonepe.devx.arnold.core.model.vcs;

import com.livetheoogway.crudstore.core.Id;

public record OAuthState(String id, String userId, VersionControlProviderType provider, long expiresAt,
                         boolean consumed) implements Id {
}
