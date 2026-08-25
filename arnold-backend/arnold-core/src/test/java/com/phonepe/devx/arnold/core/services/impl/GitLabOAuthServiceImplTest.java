package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.vcs.OAuthState;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import com.livetheoogway.crudstore.core.Versioned;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabOAuthServiceImplTest {
    private final GitLabClient client = mock(GitLabClient.class);
    private final AerospikeVersionControlCredentialStore credentialStore =
            mock(AerospikeVersionControlCredentialStore.class);
    private final AerospikeOAuthStateStore stateStore = mock(AerospikeOAuthStateStore.class);
    private final GitLabOAuthServiceImpl service = new GitLabOAuthServiceImpl(configuration(), client,
            credentialStore, stateStore);

    @Test
    void testAuthorizationUrl_shouldPersistStateForUser_whenInitiated() {
        final var authorizationUrl = service.authorizationUrl("user-1");

        assertTrue(authorizationUrl.startsWith("https://gitlab.example.com/oauth/authorize?"));
        assertTrue(authorizationUrl.contains("scope=api"));
        verify(stateStore).create(any(OAuthState.class));
    }

    @Test
    void testConnect_shouldPersistOAuthCredential_whenStateBelongsToUser() {
        final var userService = mock(UserService.class);
        final var syncService = mock(UserSyncService.class);
        final var providerIdentityService = mock(ProviderIdentityService.class);
        final var connectedService = new GitLabOAuthServiceImpl(configuration(), client, credentialStore, stateStore,
                userService, () -> syncService, providerIdentityService);
        when(stateStore.getWithVersion("state-1")).thenReturn(Optional.of(Versioned.of(
                new OAuthState("state-1", "user-1", VersionControlProviderType.GITLAB,
                        System.currentTimeMillis() + 60_000, false), 1L)));
        when(client.exchangeAuthorizationCode("code-1"))
                .thenReturn(new GitLabClient.OAuthToken("access", "refresh", 10_000L, "api read_user"));
        when(client.currentUser("access")).thenReturn(new GitLabClient.GitLabUser(42L, "gitlab-user"));
        when(credentialStore.get("gitlab:user-1")).thenReturn(Optional.empty());

        final var result = connectedService.connect("user-1", "code-1", "state-1");

        assertTrue(result.connected());
        assertEquals("42", result.providerUserId());
        assertEquals(Set.of("api", "read_user"), result.scopes());
        verify(stateStore).checkAndUpdate(any(OAuthState.class), any());
        verify(credentialStore).create(any(VersionControlCredential.class));
        verify(providerIdentityService).link(VersionControlProviderType.GITLAB, "42", "user-1");
        verify(syncService).retry("user-1");
    }

    @Test
    void testConnect_shouldRejectCallback_whenStateBelongsToAnotherUser() {
        when(stateStore.getWithVersion("state-1")).thenReturn(Optional.of(Versioned.of(
                new OAuthState("state-1", "user-2", VersionControlProviderType.GITLAB,
                        System.currentTimeMillis() + 60_000, false), 1L)));

        assertThrows(ArnoldError.class, () -> service.connect("user-1", "code-1", "state-1"));

        verify(stateStore, never()).checkAndUpdate(any(), any());
        verify(client, never()).exchangeAuthorizationCode(any());
    }

    @Test
    void testValidCredential_shouldRefreshAndPersistCredential_whenTokenIsExpiring() {
        final var credential = new VersionControlCredential("gitlab:user-1", "user-1",
                VersionControlProviderType.GITLAB, "OAUTH", "old-access", "old-refresh", "42", "gitlab-user",
                Set.of("api"), System.currentTimeMillis(), "connected", 1L, 1L);
        when(credentialStore.get("gitlab:user-1")).thenReturn(Optional.of(credential));
        when(client.refreshAccessToken("old-refresh"))
                .thenReturn(new GitLabClient.OAuthToken("new-access", "new-refresh",
                        System.currentTimeMillis() + 3_600_000, "api"));

        final var result = service.validCredential("user-1");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        verify(credentialStore).update(result);
    }

    private static GitLabConfiguration configuration() {
        return new GitLabConfiguration("https://gitlab.example.com", "client-id", "client-secret",
                "http://internal.example/api/gitlab-integrations/v1/oauth/callback", 600_000, 60_000, 30_000);
    }
}
