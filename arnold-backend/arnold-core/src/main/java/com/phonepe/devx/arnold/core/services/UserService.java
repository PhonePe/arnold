package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.user.UserResponse;
import com.phonepe.olympus.im.models.user.UserDetails;
import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;

public interface UserService {
    UserResponse getOrCreateCurrentUser(UserDetails userDetails);

    void deleteUser(String userId);

    UserResponse linkProviderIdentity(String userId, VersionControlProviderType provider, ProviderIdentity identity);
}
