package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.VersionControlConnection;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserSyncService;
import com.phonepe.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VersionControlIntegrationServiceImpl implements VersionControlIntegrationService {
    private final AerospikeVersionControlCredentialStore credentialStore;
    private final UserService userService;
    private final UserSyncService syncService;
    private final ProviderIdentityService providerIdentityService;

    @Inject
    public VersionControlIntegrationServiceImpl(final AerospikeVersionControlCredentialStore credentialStore,
                                                final UserService userService,
                                                final UserSyncService syncService,
                                                final ProviderIdentityService providerIdentityService) {
        this.credentialStore = credentialStore;
        this.userService = userService;
        this.syncService = syncService;
        this.providerIdentityService = providerIdentityService;
    }

    public VersionControlIntegrationServiceImpl(final AerospikeVersionControlCredentialStore credentialStore) {
        this(credentialStore, null, null, null);
    }

    @Override
    public VersionControlIntegrationResponse status(final VersionControlProviderType provider, final String userId) {
        return credentialStore.get(credentialId(provider, userId))
                .map(this::response)
                .orElseGet(() -> VersionControlIntegrationResponse.disconnected(provider.name()));
    }

    @Override
    public VersionControlIntegrationResponse connect(final VersionControlProviderType provider, final String userId,
                                                     final VersionControlConnection connection) {
        if ("OAUTH".equals(connection.credentialType())) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Use the provider OAuth authorization flow for OAuth credentials").build();
        }
        final var credentialId = VersionControlCredential.key(provider, userId);
        final var current = credentialStore.get(credentialId);
        final var now = System.currentTimeMillis();
        final var credential = new VersionControlCredential(credentialId, userId, provider,
                connection.credentialType(), connection.secretReference(), null, connection.providerUserId(),
                connection.providerUsername(), connection.scopes(), connection.expiresAt(), "connected",
                current.map(VersionControlCredential::createdAt).orElse(now), now);
        if (current.isPresent()) {
            credentialStore.update(credential);
        } else {
            credentialStore.create(credential);
        }
        if (userService != null && connection.providerUserId() != null) {
            userService.linkProviderIdentity(userId, provider, new ProviderIdentity(connection.providerUserId(),
                    connection.providerUsername(), null));
        }
        if (providerIdentityService != null && connection.providerUserId() != null) {
            providerIdentityService.link(provider, connection.providerUserId(), userId);
        }
        if (syncService != null) {
            syncService.retry(provider, userId);
        }
        return response(credential);
    }

    @Override
    public VersionControlIntegrationResponse disconnect(final VersionControlProviderType provider, final String userId) {
        credentialStore.delete(credentialId(provider, userId));
        if (syncService != null) {
            syncService.cancel(provider, userId);
        }
        return VersionControlIntegrationResponse.disconnected(provider.name());
    }

    private VersionControlIntegrationResponse response(final VersionControlCredential credential) {
        return new VersionControlIntegrationResponse(true, credential.provider().name(), credential.credentialType(),
                credential.providerUserId(), credential.providerUsername(), credential.scopes(), credential.expiresAt(),
                credential.status());
    }

    private String credentialId(final VersionControlProviderType provider, final String userId) {
        return VersionControlCredential.key(provider, userId);
    }
}
