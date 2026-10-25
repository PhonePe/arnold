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
        when(credentials.get("github:u1")).thenReturn(Optional.of(new VersionControlCredential("github:u1", "u1",
                VersionControlProviderType.GITHUB, "GITHUB_APP", "old", "ghr", "9", "alice", Set.of(), 1L,
                "connected", 0, 0)));
        when(client.refresh("ghr")).thenReturn(new GitHubClient.OAuthToken("new", null, Long.MAX_VALUE, null));

        assertEquals("new", integration.validCredential("u1").accessToken());
        final var stored = ArgumentCaptor.forClass(VersionControlCredential.class);
        verify(credentials).update(stored.capture());
        assertEquals("ghr", stored.getValue().refreshToken());
    }
}
