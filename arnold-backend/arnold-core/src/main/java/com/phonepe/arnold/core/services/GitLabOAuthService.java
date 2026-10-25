package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;

public interface GitLabOAuthService {
    String authorizationUrl(String userId);

    VersionControlIntegrationResponse connect(String userId, String code, String state);

    VersionControlCredential validCredential(String userId);
}
