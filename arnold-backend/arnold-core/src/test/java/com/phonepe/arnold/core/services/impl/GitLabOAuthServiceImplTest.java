/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.OAuthState;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.arnold.core.provider.gitlab.GitLabProvider;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.services.UserSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        verify(syncService).retry(VersionControlProviderType.GITLAB, "user-1");
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
        final var credential = credential(System.currentTimeMillis(), "connected");
        when(credentialStore.getWithVersion("gitlab:user-1")).thenReturn(Optional.of(Versioned.of(credential, 2L)));
        when(client.refreshAccessToken("old-refresh"))
                .thenReturn(new GitLabClient.OAuthToken("new-access", "new-refresh",
                        System.currentTimeMillis() + 3_600_000, "api"));

        final var result = service.validCredential("user-1");

        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
        verify(credentialStore).checkAndUpdate(result, 2L);
    }

    @Test
    void testValidCredential_shouldMarkReconnectRequired_whenGitLabRejectsRefreshToken() {
        when(credentialStore.getWithVersion("gitlab:user-1")).thenReturn(Optional.of(Versioned.of(
                credential(System.currentTimeMillis(), "connected"), 2L)));
        when(client.refreshAccessToken("old-refresh")).thenThrow(ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                .context(Map.of("httpStatus", 400))
                .message("GitLab request failed with HTTP 400: {\"error\":\"invalid_grant\"}").build());

        final var error = assertThrows(ArnoldError.class, () -> service.validCredential("user-1"));

        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
        assertEquals(Map.of("reason", "SCM_RECONNECT_REQUIRED", "provider", "GITLAB", "reconnect", true),
                error.getDetails());
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentialStore).checkAndUpdate(stored.capture(), eq(2L));
        assertEquals("reconnect_required", stored.getValue().status());
        verify(credentialStore, never()).delete(any());
    }

    @Test
    void testProviderCall_shouldRaiseReconnectSignal_whenGitLabRejectsUserToken() {
        when(credentialStore.getWithVersion("gitlab:user-1")).thenReturn(Optional.of(Versioned.of(
                credential(Long.MAX_VALUE, "connected"), 5L)));
        when(client.getMergeRequestDiff(1L, 2L, "old-access")).thenThrow(ArnoldError.raise(
                ArnoldErrorCode.CLIENT_FAILURE).context(Map.of("httpStatus", 401))
                .message("GitLab request failed with HTTP 401").build());

        final var error = assertThrows(ArnoldError.class,
                () -> new GitLabProvider(client, service).getChangeRequestDiff(1L, 2L, "user-1"));

        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
        assertEquals("GITLAB", error.getDetails().get("provider"));
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentialStore).checkAndUpdate(stored.capture(), eq(5L));
        assertEquals("reconnect_required", stored.getValue().status());
    }

    private static VersionControlCredential credential(final long expiresAt, final String status) {
        return new VersionControlCredential("gitlab:user-1", "user-1", VersionControlProviderType.GITLAB, "OAUTH",
                "old-access", "old-refresh", "42", "gitlab-user", Set.of("api"), expiresAt, status, 1L, 1L);
    }

    private static GitLabConfiguration configuration() {
        return new GitLabConfiguration("https://gitlab.example.com", "client-id", "client-secret",
                "http://localhost:3000/api/gitlab-integrations/v1/oauth/callback", 600_000, 60_000, 30_000);
    }
}
