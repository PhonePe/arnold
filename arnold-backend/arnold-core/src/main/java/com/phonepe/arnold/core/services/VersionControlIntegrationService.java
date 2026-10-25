package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.model.vcs.VersionControlConnection;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;

public interface VersionControlIntegrationService {
    VersionControlIntegrationResponse status(VersionControlProviderType provider, String userId);

    VersionControlIntegrationResponse connect(VersionControlProviderType provider, String userId,
                                              VersionControlConnection connection);

    VersionControlIntegrationResponse disconnect(VersionControlProviderType provider, String userId);
}
