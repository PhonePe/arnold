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

package com.phonepe.arnold.core.provider.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.OAuthState;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.storage.aerospike.AerospikeOAuthStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class GitHubIntegrationTest {

    private GitHubConfiguration config;
    private GitHubClient client;
    private AerospikeVersionControlCredentialStore credentials;
    private AerospikeOAuthStateStore states;
    private GitHubIntegration integration;

    @BeforeEach
    void setUp() {
        config = new GitHubConfiguration();
        config.setEnabled(true);
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCallbackUrl("https://arnold.example.com/github/oauth/callback");
        config.setAppSlug("arnold-review");
        client = mock(GitHubClient.class);
        credentials = mock(AerospikeVersionControlCredentialStore.class);
        states = mock(AerospikeOAuthStateStore.class);
        integration = new GitHubIntegration(config, client, new ObjectMapper(), credentials, states, null, null);
    }

    @Test
    void operationsFailClearlyWhenGitHubIsNotConfigured() {
        config.setEnabled(false);
        final var error = assertThrows(ArnoldError.class, () -> integration.validCredential("u1"));
        assertEquals(ArnoldErrorCode.OPERATION_NOT_ALLOWED, error.getErrorCode());
    }

    @Test
    void authorizationUrlStoresSingleUseState() {
        final var url = integration.authorizationUrl("u1");

        final var state = ArgumentCaptor.forClass(OAuthState.class);
        verify(states).create(state.capture());
        assertEquals(VersionControlProviderType.GITHUB, state.getValue().provider());
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize?client_id=client"));
        assertTrue(url.contains("state=" + state.getValue().id()));
        assertEquals("https://github.com/apps/arnold-review/installations/new", integration.installationUrl());
    }

    @Test
    void connectRejectsStateIssuedToAnotherUser() {
        when(states.getWithVersion("s")).thenReturn(Optional.of(Versioned.of(
                new OAuthState("s", "other", VersionControlProviderType.GITHUB, Long.MAX_VALUE, false), 1L)));

        assertThrows(ArnoldError.class, () -> integration.connect("u1", "code", "s"));
        verify(client, never()).exchangeCode(any());
    }

    @Test
    void connectStoresUserToServerToken() {
        when(states.getWithVersion("s")).thenReturn(Optional.of(Versioned.of(
                new OAuthState("s", "u1", VersionControlProviderType.GITHUB, Long.MAX_VALUE, false), 1L)));
        when(client.exchangeCode("code")).thenReturn(new GitHubClient.OAuthToken("ghu", "ghr", 5L, ""));
        when(client.currentUser("ghu")).thenReturn(new GitHubClient.GitHubUser(9, "alice", null));
        when(credentials.get("github:u1")).thenReturn(Optional.empty());

        final var response = integration.connect("u1", "code", "s");

        assertEquals("alice", response.providerUsername());
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentials).create(stored.capture());
        assertEquals("github:u1", stored.getValue().id());
        assertEquals("ghr", stored.getValue().refreshToken());
    }

    @Test
    void expiredCredentialIsRefreshedAndKeepsRefreshTokenWhenNotRotated() {
        when(credentials.getWithVersion("github:u1")).thenReturn(Optional.of(Versioned.of(credential("old", 1L,
                "connected"), 3L)));
        when(client.refresh("ghr")).thenReturn(new GitHubClient.OAuthToken("new", null, Long.MAX_VALUE, null));

        assertEquals("new", integration.validCredential("u1").accessToken());
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentials).checkAndUpdate(stored.capture(), org.mockito.ArgumentMatchers.eq(3L));
        assertEquals("ghr", stored.getValue().refreshToken());
    }

    @Test
    void userTokenRejectedByGitHubMarksReconnectRequiredAndRaisesSignal() {
        when(credentials.getWithVersion("github:u1")).thenReturn(Optional.of(Versioned.of(credential("ghu",
                Long.MAX_VALUE, "connected"), 4L)));
        final var unauthorized = ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .context(java.util.Map.of("httpStatus", 401, "rateLimited", false))
                .message("GitHub request failed with HTTP 401: Bad credentials").build();
        when(client.diff(1L, 2L, "ghu")).thenThrow(unauthorized);

        final var error = assertThrows(ArnoldError.class,
                () -> new GitHubProvider(integration).getChangeRequestDiff(1L, 2L, "u1"));

        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
        assertEquals(java.util.Map.of("reason", "SCM_RECONNECT_REQUIRED", "provider", "GITHUB", "reconnect", true),
                error.getDetails());
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentials).checkAndUpdate(stored.capture(), org.mockito.ArgumentMatchers.eq(4L));
        assertEquals("reconnect_required", stored.getValue().status());
        assertEquals("ghr", stored.getValue().refreshToken());
        verify(credentials, never()).delete(any());
    }

    @Test
    void reconnectRequiredCredentialIsReportedWithoutCallingGitHub() {
        when(credentials.getWithVersion("github:u1")).thenReturn(Optional.of(Versioned.of(credential("ghu",
                Long.MAX_VALUE, "reconnect_required"), 4L)));

        final var error = assertThrows(ArnoldError.class, () -> integration.validCredential("u1"));

        assertEquals(true, error.getDetails().get("reconnect"));
        verify(client, never()).refresh(any());
    }

    @Test
    void appAuthenticationFailureIsActionableAndDropsCachedInstallationTokens() throws Exception {
        configureApp();
        when(client.installationToken(org.mockito.ArgumentMatchers.eq(5L), any(), any()))
                .thenReturn(new GitHubClient.OAuthToken("ghs_1", null, System.currentTimeMillis() + 3_600_000, null))
                .thenReturn(new GitHubClient.OAuthToken("ghs_2", null, System.currentTimeMillis() + 3_600_000, null));
        assertEquals("ghs_1", integration.installationAccessToken(5L));
        assertEquals("ghs_1", integration.installationAccessToken(5L));

        final var translated = integration.appAuthenticationFailure(ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .context(java.util.Map.of("httpStatus", 401)).message("GitHub request failed with HTTP 401").build());

        assertEquals(ArnoldErrorCode.CLIENT_FAILURE, translated.getErrorCode());
        assertTrue(GitHubIntegration.isAppAuthenticationFailure(translated));
        assertTrue(translated.getMessage().startsWith("GitHub App authentication failed (HTTP 401). Check "
                + "GITHUB_APP_ID/GITHUB_APP_PRIVATE_KEY and that the server clock is correct; server time is "));
        assertTrue(translated.getDetails().isEmpty());
        assertEquals("ghs_2", integration.installationAccessToken(5L));
        verify(client, org.mockito.Mockito.times(2)).installationToken(any(Long.class), any(), any());
    }

    @Test
    void installationListingRejectedWithHttp401DoesNotLookLikeAUserProblem() throws Exception {
        configureApp();
        when(client.installations(any())).thenThrow(ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .context(java.util.Map.of("httpStatus", 401)).message("GitHub request failed with HTTP 401").build());

        final var error = assertThrows(ArnoldError.class, () -> integration.installations());

        assertEquals(ArnoldErrorCode.CLIENT_FAILURE, error.getErrorCode());
        assertTrue(error.getMessage().contains("server clock"));
    }

    private void configureApp() throws Exception {
        final var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        config.setAppId(7);
        config.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
    }

    private static VersionControlCredential credential(final String access, final long expiresAt,
                                                       final String status) {
        return new VersionControlCredential("github:u1", "u1", VersionControlProviderType.GITHUB, "GITHUB_APP",
                access, "ghr", "9", "alice", Set.of(), expiresAt, status, 0, 0);
    }

    @Test
    void mintsAndCachesRepositoryScopedInstallationTokens() throws Exception {
        final var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        config.setAppId(7);
        config.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        when(client.installationId(org.mockito.ArgumentMatchers.eq("acme/api"), any())).thenReturn(11L);
        when(client.installationToken(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("api")), any()))
                .thenReturn(new GitHubClient.OAuthToken("ghs_1", null, System.currentTimeMillis() + 3_600_000, null));

        assertEquals(Optional.of("ghs_1"), integration.installationTokenForPullRequest("https://github.com/acme/api/pull/3"));
        assertEquals(Optional.of("ghs_1"), integration.installationTokenForPullRequest("https://github.com/acme/api/pull/4"));
        verify(client, org.mockito.Mockito.times(1)).installationToken(any(Long.class), any(), any());
        assertEquals(Optional.empty(), integration.installationTokenForPullRequest("https://gitlab.example.com/a/b/-/merge_requests/1"));
    }
}
