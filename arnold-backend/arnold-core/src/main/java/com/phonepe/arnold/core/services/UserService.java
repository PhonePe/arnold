package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.user.UserResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import com.phonepe.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

import java.util.List;

public interface UserService {
    UserResponse getOrCreateCurrentUser(ArnoldPrincipal principal);

    List<UserResponse> list();

    void deleteUser(String userId);

    UserResponse linkProviderIdentity(String userId, VersionControlProviderType provider, ProviderIdentity identity);
}
