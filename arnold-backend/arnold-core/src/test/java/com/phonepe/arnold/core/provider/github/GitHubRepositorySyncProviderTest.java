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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.config.RepositorySyncConfiguration;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.sync.RepositoryChangeRequestImporter;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubRepositorySyncProviderTest {
    private static final String PULL = """
            {"id":%d,"number":%d,"title":"PR %d","state":"%s","draft":false,"html_url":"https://github.com/acme/api/pull/%d",
             "merged_at":%s,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z",
             "user":{"id":1,"login":"alice"},"assignees":[],"requested_reviewers":[{"id":3,"login":"carol"}],
             "labels":[],"head":{"ref":"feature","sha":"sha-%d"},
             "base":{"ref":"main","sha":"base","repo":{"id":42,"full_name":"acme/api"}}}""";

    private WireMockServer server;
    private GitHubRepositorySyncProvider provider;
    private ChangeRequestService changeRequests;
    private VersionControlSyncService sync;
    private UserMergeRequestService userMergeRequests;
    private ProviderIdentityService identities;

    @BeforeEach
    void setUp() throws Exception {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        final var config = new GitHubConfiguration();
        config.setEnabled(true);
        config.setWebUrl(server.baseUrl());
        config.setApiUrl(server.baseUrl());
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCallbackUrl("https://arnold.example.com/github/oauth/callback");
        config.setAppId(99);
        config.setPrivateKey(privateKey());
        final var client = new GitHubClient(config, new ObjectMapper(), new OkHttpClient(), Clock.systemUTC());
        final var integration = new GitHubIntegration(config, client, new ObjectMapper(), null, null, null, null);
        changeRequests = mock(ChangeRequestService.class);
        sync = mock(VersionControlSyncService.class);
        userMergeRequests = mock(UserMergeRequestService.class);
        identities = mock(ProviderIdentityService.class);
        when(changeRequests.record(anyString())).thenReturn(Optional.empty());
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(true);
        when(identities.findUserId(any(), anyString())).thenReturn(Optional.empty());
        when(identities.findUserId(VersionControlProviderType.GITHUB, "3")).thenReturn(Optional.of("carol-arnold"));
        final var importer = new RepositoryChangeRequestImporter(changeRequests, sync, userMergeRequests, identities,
                mock(MergeQueueService.class));
        provider = new GitHubRepositorySyncProvider(integration, importer, RepositorySyncConfiguration.defaults());

        server.stubFor(get(urlEqualTo("/app/installations?per_page=100")).willReturn(aResponse()
                .withHeader("Link", "<" + server.baseUrl() + "/app/installations?per_page=100&page=2>; rel=\"next\"")
                .withBody("[{\"id\":7}]")));
        server.stubFor(get(urlEqualTo("/app/installations?per_page=100&page=2")).willReturn(aResponse()
                .withBody("[]")));
        server.stubFor(post(urlEqualTo("/app/installations/7/access_tokens")).willReturn(aResponse()
                .withBody("{\"token\":\"inst-token\",\"expires_at\":\"2099-01-01T00:00:00Z\"}")));
        server.stubFor(get(urlPathEqualTo("/installation/repositories")).willReturn(aResponse().withBody("""
                {"total_count":2,"repositories":[{"id":42,"full_name":"acme/api","archived":false},
                 {"id":43,"full_name":"acme/old","archived":true}]}""")));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void importsOpenPullRequestsAndAssociatesLinkedUsers() {
        server.stubFor(get(urlEqualTo("/repos/acme/api/pulls?state=open&per_page=100")).willReturn(aResponse()
                .withBody("[" + pull(501, 1, "open", false) + "," + pull(502, 2, "open", false) + "]")));
        when(changeRequests.list()).thenReturn(List.of());

        final var result = provider.synchronize();

        assertTrue(provider.available());
        assertNull(result.failure());
        assertEquals(1, result.repositories());
        assertEquals(2, result.changeRequests());
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, times(2)).synchronize(captor.capture());
        assertEquals("github:42:1", captor.getAllValues().get(0).changeRequest().id());
        assertEquals("opened", captor.getAllValues().get(0).changeRequest().state());
        assertEquals(ChangeRequestSync.Action.UPSERT, captor.getAllValues().get(0).action());
        verify(userMergeRequests, times(2)).upsert(eq("carol-arnold"), any(ChangeRequestRecord.class),
                eq(Set.of(MergeRequestRole.REVIEWER)), anyLong());
        server.verify(0, getRequestedFor(urlEqualTo("/repos/acme/old/pulls?state=open&per_page=100")));
    }

    @Test
    void refreshesStoredOpenPullRequestsThatLeftTheOpenList() {
        server.stubFor(get(urlEqualTo("/repos/acme/api/pulls?state=open&per_page=100")).willReturn(aResponse()
                .withBody("[" + pull(501, 1, "open", false) + "]")));
        server.stubFor(get(urlEqualTo("/repositories/42")).willReturn(aResponse()
                .withBody("{\"full_name\":\"acme/api\"}")));
        server.stubFor(get(urlEqualTo("/repos/acme/api/pulls/9")).willReturn(aResponse()
                .withBody(pull(509, 9, "closed", true))));
        when(changeRequests.list()).thenReturn(List.of(stored(1), stored(9), new ChangeRequestRecord("github:77:1",
                VersionControlProviderType.GITHUB, "77", "1", 1, "other/repo", "t", null, null, "f", "main", "s",
                "opened", null, false, 1L, List.of(), List.of(), List.of(), 1, 1)));

        provider.synchronize();

        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, times(2)).synchronize(captor.capture());
        final var refreshed = captor.getAllValues().get(1);
        assertEquals("github:42:9", refreshed.changeRequest().id());
        assertEquals(ChangeRequestSync.Action.MERGED, refreshed.action());
    }

    @Test
    void stopsAndReportsWhenRateLimited() {
        server.stubFor(get(urlEqualTo("/repos/acme/api/pulls?state=open&per_page=100")).willReturn(aResponse()
                .withStatus(403).withHeader("X-RateLimit-Remaining", "0").withBody("{\"message\":\"rate limit\"}")));

        final var result = provider.synchronize();

        assertNotNull(result.failure());
        assertEquals(0, result.repositories());
        verify(changeRequests, never()).list();
        verify(sync, never()).synchronize(any(ChangeRequestSync.class));
    }

    @Test
    void isUnavailableWithoutAppCredentials() {
        final var config = new GitHubConfiguration();
        config.setEnabled(true);
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCallbackUrl("https://arnold.example.com/cb");
        final var integration = new GitHubIntegration(config, mock(GitHubClient.class), new ObjectMapper(), null,
                null, null, null);
        assertFalse(new GitHubRepositorySyncProvider(integration, null, RepositorySyncConfiguration.defaults()).available());
    }

    private static ChangeRequestRecord stored(final long number) {
        return new ChangeRequestRecord("github:42:" + number, VersionControlProviderType.GITHUB, "42",
                Long.toString(500 + number), number, "acme/api", "t", null, null, "f", "main", "s", "opened", null,
                false, 1L, List.of(), List.of(), List.of(), 1, number);
    }

    private static String pull(final long id, final long number, final String state, final boolean merged) {
        return PULL.formatted(id, number, number, state, number, merged ? "\"2026-01-03T00:00:00Z\"" : "null",
                number);
    }

    private static String privateKey() throws Exception {
        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final var der = generator.generateKeyPair().getPrivate().getEncoded();
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END PRIVATE KEY-----";
    }
}
