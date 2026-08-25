package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.OAuthState;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.phonepe.devx.arnold.models.integration.VersionControlIntegrationResponse;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Singleton
public class GitLabOAuthServiceImpl implements GitLabOAuthService {
    private final GitLabConfiguration configuration;
    private final GitLabClient client;
    private final AerospikeVersionControlCredentialStore credentialStore;
    private final AerospikeOAuthStateStore stateStore;
    private final UserService userService;
    private final Provider<UserSyncService> syncServiceProvider;
    private final ProviderIdentityService providerIdentityService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public GitLabOAuthServiceImpl(final @Config GitLabConfiguration configuration,
                                  final GitLabClient client,
                                  final AerospikeVersionControlCredentialStore credentialStore,
                                  final AerospikeOAuthStateStore stateStore,
                                  final UserService userService,
                                  final Provider<UserSyncService> syncServiceProvider,
                                  final ProviderIdentityService providerIdentityService) {
        this.configuration = configuration;
        this.client = client;
        this.credentialStore = credentialStore;
        this.stateStore = stateStore;
        this.userService = userService;
        this.syncServiceProvider = syncServiceProvider;
        this.providerIdentityService = providerIdentityService;
    }

    public GitLabOAuthServiceImpl(final GitLabConfiguration configuration,
                                  final GitLabClient client,
                                  final AerospikeVersionControlCredentialStore credentialStore,
                                  final AerospikeOAuthStateStore stateStore) {
        this(configuration, client, credentialStore, stateStore, null, null, null);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Set<String> scopes(final String scope) {
        return scope == null || scope.isBlank() ? Set.of() : Set.of(scope.trim().split("\\s+"));
    }

    private static VersionControlIntegrationResponse response(final VersionControlCredential credential) {
        return new VersionControlIntegrationResponse(true, credential.provider().name(), credential.credentialType(),
                credential.providerUserId(), credential.providerUsername(), credential.scopes(), credential.expiresAt(),
                credential.status());
    }

    private static RuntimeException invalidState() {
        return ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                .message("Invalid or expired GitLab OAuth state").build();
    }

    @Override
    public String authorizationUrl(final String userId) {
        final var state = generateState();
        stateStore.create(new OAuthState(state, userId, VersionControlProviderType.GITLAB,
                System.currentTimeMillis() + configuration.oauthStateTtlMs(), false));
        return configuration.baseUrl().replaceAll("/+$", "") + "/oauth/authorize?client_id="
                + encode(configuration.clientId()) + "&redirect_uri=" + encode(configuration.callbackUrl())
                + "&response_type=code&state=" + encode(state) + "&scope=api";
    }

    @Override
    public VersionControlIntegrationResponse connect(final String userId, final String code, final String state) {
        consumeState(userId, state);
        final var token = client.exchangeAuthorizationCode(code);
        final var gitLabUser = client.currentUser(token.accessToken());
        final var now = System.currentTimeMillis();
        final var id = VersionControlCredential.key(VersionControlProviderType.GITLAB, userId);
        final var current = credentialStore.get(id);
        final var credential = new VersionControlCredential(id, userId, VersionControlProviderType.GITLAB, "OAUTH",
                token.accessToken(), token.refreshToken(), Long.toString(gitLabUser.id()), gitLabUser.username(),
                scopes(token.scope()), token.expiresAt(), "connected",
                current.map(VersionControlCredential::createdAt).orElse(now), now);
        if (current.isPresent()) {
            credentialStore.update(credential);
        } else {
            credentialStore.create(credential);
        }
        if (userService != null) {
            userService.linkProviderIdentity(userId, VersionControlProviderType.GITLAB,
                    new ProviderIdentity(Long.toString(gitLabUser.id()), gitLabUser.username(), null));
        }
        if (providerIdentityService != null) {
            providerIdentityService.link(VersionControlProviderType.GITLAB, Long.toString(gitLabUser.id()), userId);
        }
        if (syncServiceProvider != null) {
            syncServiceProvider.get().retry(VersionControlProviderType.GITLAB, userId);
        }
        return response(credential);
    }

    @Override
    public VersionControlCredential validCredential(final String userId) {
        final var id = VersionControlCredential.key(VersionControlProviderType.GITLAB, userId);
        final var credential = credentialStore.get(id)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                        .message("GitLab account is not connected").build());
        if (credential.expiresAt() == null
                || System.currentTimeMillis() < credential.expiresAt() - configuration.tokenRefreshBufferMs()) {
            return credential;
        }
        if (credential.refreshToken() == null || credential.refreshToken().isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).message("GitLab credential has expired").build();
        }
        final var token = client.refreshAccessToken(credential.refreshToken());
        final var refreshed = new VersionControlCredential(credential.id(), credential.userId(), credential.provider(),
                credential.credentialType(), token.accessToken(), token.refreshToken(), credential.providerUserId(),
                credential.providerUsername(), scopes(token.scope()), token.expiresAt(), credential.status(),
                credential.createdAt(), System.currentTimeMillis());
        credentialStore.update(refreshed);
        return refreshed;
    }

    private void consumeState(final String userId, final String state) {
        final var versioned = stateStore.getWithVersion(state)
                .orElseThrow(() -> invalidState());
        final var stored = versioned.value();
        if (!userId.equals(stored.userId()) || stored.provider() != VersionControlProviderType.GITLAB
                || stored.expiresAt() < System.currentTimeMillis() || stored.consumed()) {
            throw invalidState();
        }
        try {
            stateStore.checkAndUpdate(new OAuthState(stored.id(), stored.userId(), stored.provider(),
                    stored.expiresAt(), true), versioned.version());
        } catch (ConcurrentUpdateException conflict) {
            throw invalidState();
        }
    }

    private String generateState() {
        final var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
