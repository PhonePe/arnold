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
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GitHub App integration: user connections through the app's OAuth flow (user-to-server tokens with refresh) and
 * installation tokens for acting as the app.
 */
@Singleton
public class GitHubIntegration {
    private static final long INSTALLATION_TOKEN_BUFFER_MS = 5 * 60_000;

    private final GitHubConfiguration config;
    private final GitHubClient client;
    private final AerospikeVersionControlCredentialStore credentials;
    private final AerospikeOAuthStateStore states;
    private final UserService users;
    private final ProviderIdentityService identities;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, GitHubClient.OAuthToken> installationTokens = new ConcurrentHashMap<>();
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
        final var credential = credentials.get(VersionControlCredential.key(VersionControlProviderType.GITHUB, userId))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                        .message("GitHub account is not connected").build());
        if (credential.expiresAt() == null
                || System.currentTimeMillis() < credential.expiresAt() - config.getTokenRefreshBufferMs()) {
            return credential;
        }
        if (credential.refreshToken() == null || credential.refreshToken().isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).message("GitHub credential has expired").build();
        }
        final var token = client.refresh(credential.refreshToken());
        final var refreshed = new VersionControlCredential(credential.id(), credential.userId(), credential.provider(),
                credential.credentialType(), token.accessToken(),
                token.refreshToken() == null ? credential.refreshToken() : token.refreshToken(),
                credential.providerUserId(), credential.providerUsername(), scopes(token.scope()), token.expiresAt(),
                credential.status(), credential.createdAt(), System.currentTimeMillis());
        credentials.update(refreshed);
        return refreshed;
    }

    /**
     * Mints (or reuses) an installation token scoped to a single repository, for acting as the app itself.
     *
     * @param fullName repository as {@code owner/name}
     */
    public String installationToken(final String fullName) {
        requireConfigured();
        final var cached = installationTokens.get(fullName);
        if (cached != null && cached.expiresAt() - INSTALLATION_TOKEN_BUFFER_MS > System.currentTimeMillis()) {
            return cached.accessToken();
        }
        final var app = app();
        final var token = client.installationToken(client.installationId(fullName, app),
                java.util.List.of(fullName.substring(fullName.indexOf('/') + 1)), app);
        installationTokens.put(fullName, token);
        return token.accessToken();
    }

    /**
     * @return an installation token for the repository of a GitHub pull request URL, or empty when the URL is not
     * a GitHub pull request or the app credentials are not configured
     */
    public java.util.Optional<String> installationTokenForPullRequest(final String url) {
        if (!config.configured() || config.getAppId() <= 0 || config.getPrivateKey() == null
                || config.getPrivateKey().isBlank() || url == null) {
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
