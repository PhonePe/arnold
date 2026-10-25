package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

import java.util.Optional;

public interface ProviderIdentityService {
    void link(VersionControlProviderType provider, String providerUserId, String userId);

    Optional<String> findUserId(VersionControlProviderType provider, String providerUserId);
}
