/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.provider.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.OAuthState;
import com.phonepe.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.OAuthCredentialManager;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * GitHub App integration: user connections through the app's OAuth flow (user-to-server tokens with refresh) and
 * installation tokens for acting as the app.
 */
@Slf4j
@Singleton
public class GitHubIntegration {
    private static final long INSTALLATION_TOKEN_BUFFER_MS = 5 * 60_000;
    private static final String APP_AUTH_FAILURE = "githubAppAuthenticationFailed";

    private final GitHubConfiguration config;
    private final GitHubClient client;
    private final AerospikeVersionControlCredentialStore credentials;
    private final AerospikeOAuthStateStore states;
    private final UserService users;
    private final ProviderIdentityService identities;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, GitHubClient.OAuthToken> installationTokens = new ConcurrentHashMap<>();
    private final Striped<Lock> installationTokenLocks = Striped.lazyWeakLock(32);
    private final AtomicBoolean appAuthFailureLogged = new AtomicBoolean();
    private final OAuthCredentialManager userCredentials;
    private final Clock clock;
    private volatile GitHubAppAuth appAuth;

    @Inject
    public GitHubIntegration(final ArnoldCoreConfiguration configuration, final ObjectMapper mapper,
                             final AerospikeVersionControlCredentialStore credentials,
                             final AerospikeOAuthStateStore states, final UserService users,
                             final ProviderIdentityService identities) {
        this(configuration.getGithubConfiguration() == null ? new GitHubConfiguration()
                        : configuration.getGithubConfiguration(), null, mapper, credentials, states, users, identities);
    }

    GitHubIntegration(final GitHubConfiguration config, final GitHubClient client, final ObjectMapper mapper,
                      final AerospikeVersionControlCredentialStore credentials, final AerospikeOAuthStateStore states,
                      final UserService users, final ProviderIdentityService identities) {
        this.config = config;
        this.client = client != null ? client : new GitHubClient(config, mapper);
        this.credentials = credentials;
        this.states = states;
        this.users = users;
        this.identities = identities;
        this.clock = Clock.systemUTC();
        this.userCredentials = new OAuthCredentialManager(credentials, VersionControlProviderType.GITHUB,
                config.getTokenRefreshBufferMs(), refreshToken -> {
                    final var token = this.client.refresh(refreshToken);
                    return new OAuthCredentialManager.Token(token.accessToken(), token.refreshToken(),
                            token.expiresAt(), scopes(token.scope()));
                }, clock);
    }

    public boolean configured() {
        return config.configured();
    }

    /**
     * @return whether the app can authenticate as itself (app id and private key are set)
     */
    public boolean appConfigured() {
        return config.configured() && config.getAppId() > 0 && config.getPrivateKey() != null
                && !config.getPrivateKey().isBlank();
    }

    /**
     * @return ids of every installation of the app
     */
    public java.util.List<Long> installations() {
        requireConfigured();
        return asApp(() -> client.installations(app()));
    }

    /**
     * Mints (or reuses) a token covering every repository of an installation.
     */
    public String installationAccessToken(final long installationId) {
        requireConfigured();
        return cachedInstallationToken("installation:" + installationId,
                () -> client.installationToken(installationId, java.util.List.of(), app()));
    }

    /**
     * Translates an HTTP 401 on an app-authenticated call (app JWT or installation token) into an actionable
     * failure. Such a 401 is never a user problem: the app id or private key is wrong, or the server clock is off
     * far enough that GitHub rejects the JWT's issue/expiry times. Cached installation tokens are dropped so the
     * next attempt mints fresh ones.
     *
     * @return the failure to throw, or {@code error} itself when it is not an authentication failure
     */
    public ArnoldError appAuthenticationFailure(final ArnoldError error) {
        if (isAppAuthenticationFailure(error) || !OAuthCredentialManager.unauthorized(error)) {
            return error;
        }
        installationTokens.clear();
        final var message = "GitHub App authentication failed (HTTP 401). Check GITHUB_APP_ID/GITHUB_APP_PRIVATE_KEY"
                + " and that the server clock is correct; server time is " + Instant.now(clock);
        if (appAuthFailureLogged.compareAndSet(false, true)) {
            log.error(message);
        }
        return ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).context(Map.of(APP_AUTH_FAILURE, true))
                .message(message).build();
    }

    public static boolean isAppAuthenticationFailure(final ArnoldError error) {
        return error.getContext() != null && Boolean.TRUE.equals(error.getContext().get(APP_AUTH_FAILURE));
    }

    private <T> T asApp(final Supplier<T> call) {
        try {
            final var result = call.get();
            appAuthFailureLogged.set(false);
            return result;
        } catch (ArnoldError error) {
            throw appAuthenticationFailure(error);
        }
    }

    private String cachedInstallationToken(final String key, final Supplier<GitHubClient.OAuthToken> mint) {
        final var cached = installationTokens.get(key);
        if (usable(cached)) {
            return cached.accessToken();
        }
        final var lock = installationTokenLocks.get(key);
        lock.lock();
        try {
            final var current = installationTokens.get(key);
            if (usable(current)) {
                return current.accessToken();
            }
            final var token = asApp(mint);
            installationTokens.put(key, token);
            return token.accessToken();
        } finally {
            lock.unlock();
        }
    }

    private boolean usable(final GitHubClient.OAuthToken token) {
        return token != null && token.expiresAt() != null
                && token.expiresAt() - INSTALLATION_TOKEN_BUFFER_MS > clock.millis();
    }

    public GitHubClient client() {
        requireConfigured();
        return client;
    }

    public String authorizationUrl(final String userId) {
        requireConfigured();
        final var bytes = new byte[32];
        random.nextBytes(bytes);
        final var state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.create(new OAuthState(state, userId, VersionControlProviderType.GITHUB,
                System.currentTimeMillis() + config.getOauthStateTtlMs(), false));
        return config.getWebUrl().replaceAll("/+$", "") + "/login/oauth/authorize?client_id="
                + encode(config.getClientId()) + "&redirect_uri=" + encode(config.getCallbackUrl())
                + "&state=" + encode(state);
    }

    /**
     * @return where users install the app on their repositories, or null when the slug is not configured
     */
    public String installationUrl() {
        return config.getAppSlug() == null || config.getAppSlug().isBlank() ? null
                : config.getWebUrl().replaceAll("/+$", "") + "/apps/" + config.getAppSlug() + "/installations/new";
    }

    public VersionControlIntegrationResponse connect(final String userId, final String code, final String state) {
        requireConfigured();
        consumeState(userId, state);
        final var token = client.exchangeCode(code);
        final var user = client.currentUser(token.accessToken());
        final var now = System.currentTimeMillis();
        final var id = VersionControlCredential.key(VersionControlProviderType.GITHUB, userId);
        final var current = credentials.get(id);
        final var credential = new VersionControlCredential(id, userId, VersionControlProviderType.GITHUB,
                "GITHUB_APP", token.accessToken(), token.refreshToken(), Long.toString(user.id()), user.login(),
                scopes(token.scope()), token.expiresAt(), "connected",
                current.map(VersionControlCredential::createdAt).orElse(now), now);
        if (current.isPresent()) {
            credentials.update(credential);
        } else {
            credentials.create(credential);
        }
        if (users != null) {
            users.linkProviderIdentity(userId, VersionControlProviderType.GITHUB,
                    new ProviderIdentity(Long.toString(user.id()), user.login(), user.avatarUrl()));
        }
        if (identities != null) {
            identities.link(VersionControlProviderType.GITHUB, Long.toString(user.id()), userId);
        }
        return new VersionControlIntegrationResponse(true, VersionControlProviderType.GITHUB.name(),
                credential.credentialType(), credential.providerUserId(), credential.providerUsername(),
                credential.scopes(), credential.expiresAt(), credential.status());
    }

    public VersionControlCredential validCredential(final String userId) {
        requireConfigured();
        return userCredentials.valid(userId);
    }

    /**
     * Runs a GitHub call with the user's token. A 401 marks the credential {@code reconnect_required} and raises the
     * reconnect signal, unless the token was rotated concurrently, in which case the call is retried once.
     */
    public <T> T withUserToken(final String userId, final Function<VersionControlCredential, T> call) {
        var credential = validCredential(userId);
        try {
            return call.apply(credential);
        } catch (ArnoldError error) {
            if (!OAuthCredentialManager.unauthorized(error)) {
                throw error;
            }
            credential = userCredentials.unauthorized(userId, credential);
        }
        try {
            return call.apply(credential);
        } catch (ArnoldError error) {
            if (OAuthCredentialManager.unauthorized(error)) {
                userCredentials.unauthorized(userId, credential);
            }
            throw error;
        }
    }

    /**
     * Mints (or reuses) an installation token scoped to a single repository, for acting as the app itself.
     *
     * @param fullName repository as {@code owner/name}
     */
    public String installationToken(final String fullName) {
        requireConfigured();
        return cachedInstallationToken(fullName, () -> {
            final var app = app();
            return client.installationToken(client.installationId(fullName, app),
                    java.util.List.of(fullName.substring(fullName.indexOf('/') + 1)), app);
        });
    }

    /**
     * @return an installation token for the repository of a GitHub pull request URL, or empty when the URL is not
     * a GitHub pull request or the app credentials are not configured
     */
    public java.util.Optional<String> installationTokenForPullRequest(final String url) {
        if (!appConfigured() || url == null) {
            return java.util.Optional.empty();
        }
        final var matcher = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(
                config.getWebUrl().replaceAll("/+$", "")) + "/([\\w.-]+/[\\w.-]+)/pull/\\d+(?:[/?#].*)?$")
                .matcher(url.trim());
        return matcher.matches() ? java.util.Optional.of(installationToken(matcher.group(1)))
                : java.util.Optional.empty();
    }

    private GitHubAppAuth app() {
        var current = appAuth;
        if (current == null) {
            synchronized (this) {
                if (appAuth == null) {
                    appAuth = new GitHubAppAuth(config.getAppId(), config.getPrivateKey());
                }
                current = appAuth;
            }
        }
        return current;
    }

    private void consumeState(final String userId, final String state) {
        final var versioned = states.getWithVersion(state).orElseThrow(GitHubIntegration::invalidState);
        final var stored = versioned.value();
        if (!userId.equals(stored.userId()) || stored.provider() != VersionControlProviderType.GITHUB
                || stored.expiresAt() < System.currentTimeMillis() || stored.consumed()) {
            throw invalidState();
        }
        try {
            states.checkAndUpdate(new OAuthState(stored.id(), stored.userId(), stored.provider(), stored.expiresAt(),
                    true), versioned.version());
        } catch (ConcurrentUpdateException conflict) {
            throw invalidState();
        }
    }

    private void requireConfigured() {
        if (!config.configured()) {
            throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED)
                    .message("GitHub integration is not configured").build();
        }
    }

    private static RuntimeException invalidState() {
        return ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid or expired GitHub OAuth state")
                .build();
    }

    private static Set<String> scopes(final String scope) {
        return scope == null || scope.isBlank() ? Set.of() : Set.of(scope.trim().split("[\\s,]+"));
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
