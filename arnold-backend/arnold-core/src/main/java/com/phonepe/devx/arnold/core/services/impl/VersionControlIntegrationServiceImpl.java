package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlConnection;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.phonepe.devx.arnold.models.integration.VersionControlIntegrationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VersionControlIntegrationServiceImpl implements VersionControlIntegrationService {
    private final AerospikeVersionControlCredentialStore credentialStore;

    @Inject
    public VersionControlIntegrationServiceImpl(final AerospikeVersionControlCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
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
                    .message("Use the GitLab OAuth authorization flow for OAuth credentials").build();
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
        return response(credential);
    }

    @Override
    public VersionControlIntegrationResponse disconnect(final VersionControlProviderType provider, final String userId) {
        credentialStore.delete(credentialId(provider, userId));
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
