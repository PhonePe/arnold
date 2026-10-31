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
package com.phonepe.arnold.core.provider.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.arnold.core.config.GitLabConfiguration;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabRepositorySyncProviderTest {
    private static final String MERGE_REQUEST = """
            {"id":%d,"iid":%d,"project_id":%d,"title":"MR %d","state":"opened","draft":false,
             "web_url":"http://gitlab.local/acme/api/-/merge_requests/%d","source_branch":"feature",
             "target_branch":"main","sha":"sha","detailed_merge_status":"mergeable",
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z",
             "author":{"id":5,"username":"alice"},"assignees":[],"reviewers":[],"labels":[]}""";

    private WireMockServer gitLab;
    private ChangeRequestService changeRequests;
    private VersionControlSyncService sync;
    private UserMergeRequestService userMergeRequests;
    private GitLabConfiguration configuration;
    private GitLabClientImpl client;
    private RepositoryChangeRequestImporter importer;

    @BeforeEach
    void setUp() {
        gitLab = new WireMockServer(0);
        gitLab.start();
        configuration = new GitLabConfiguration("http://localhost:" + gitLab.port(), "client", "secret",
                "http://localhost/callback", 600_000, 60_000, 30_000, null, "service-token");
        client = new GitLabClientImpl(configuration, new ObjectMapper());
        changeRequests = mock(ChangeRequestService.class);
        sync = mock(VersionControlSyncService.class);
        userMergeRequests = mock(UserMergeRequestService.class);
        final var identities = mock(ProviderIdentityService.class);
        when(changeRequests.record(anyString())).thenReturn(Optional.empty());
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(true);
        when(identities.findUserId(any(), anyString())).thenReturn(Optional.empty());
        when(identities.findUserId(VersionControlProviderType.GITLAB, "5")).thenReturn(Optional.of("alice-arnold"));
        importer = new RepositoryChangeRequestImporter(changeRequests, sync, userMergeRequests, identities,
                mock(MergeQueueService.class));
    }

    @AfterEach
    void tearDown() {
        gitLab.stop();
    }

    @Test
    void importsOpenMergeRequestsOfConfiguredGroupsAcrossPages() {
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/groups/acme%2Fplatform/projects"))
                .withQueryParam("include_subgroups", equalTo("true"))
                .withQueryParam("archived", equalTo("false"))
                .withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.absent())
                .willReturn(aResponse().withHeader("X-Next-Page", "2")
                        .withBody("[{\"id\":10,\"path_with_namespace\":\"acme/platform/api\",\"archived\":false}]")));
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/groups/acme%2Fplatform/projects"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withHeader("X-Next-Page", "")
                        .withBody("[{\"id\":11,\"path_with_namespace\":\"acme/platform/old\",\"archived\":true}]")));
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects/10/merge_requests"))
                .withQueryParam("state", equalTo("opened"))
                .willReturn(aResponse().withBody("[" + MERGE_REQUEST.formatted(100, 1, 10, 1, 1) + ","
                        + MERGE_REQUEST.formatted(101, 2, 10, 2, 2) + "]")));
        when(changeRequests.list()).thenReturn(List.of());
        final var provider = new GitLabRepositorySyncProvider(configuration,
                new RepositorySyncConfiguration(true, 60, 0, 0, 0, List.of("acme/platform")), client, importer);

        final var result = provider.synchronize();

        assertTrue(provider.available());
        assertNull(result.failure());
        assertEquals(1, result.repositories());
        assertEquals(2, result.changeRequests());
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, times(2)).synchronize(captor.capture());
        assertEquals("gitlab:10:1", captor.getAllValues().get(0).changeRequest().id());
        verify(userMergeRequests, times(2)).upsert(eq("alice-arnold"), any(ChangeRequestRecord.class),
                eq(Set.of(MergeRequestRole.AUTHOR)), anyLong());
        gitLab.verify(getRequestedFor(urlPathEqualTo("/api/v4/groups/acme%2Fplatform/projects"))
                .withHeader("Authorization", equalTo("Bearer service-token")));
        gitLab.verify(0, getRequestedFor(urlPathEqualTo("/api/v4/projects/11/merge_requests")));
    }

    @Test
    void usesTokenMembershipWithoutGroupsAndSkipsMissingStaleMergeRequests() {
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects"))
                .withQueryParam("membership", equalTo("true"))
                .willReturn(aResponse().withBody("[{\"id\":10,\"path_with_namespace\":\"acme/api\"}]")));
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects/10/merge_requests"))
                .willReturn(aResponse().withBody("[]")));
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects/10/merge_requests/7"))
                .willReturn(aResponse().withStatus(404).withBody("{}")));
        when(changeRequests.list()).thenReturn(List.of(new ChangeRequestRecord("gitlab:10:7",
                VersionControlProviderType.GITLAB, "10", "70", 7, "acme/api", "t", null, null, "f", "main", "s",
                "opened", null, false, 5L, List.of(), List.of(), List.of(), 1, 1)));
        final var provider = new GitLabRepositorySyncProvider(configuration, (RepositorySyncConfiguration) null, client, importer);

        final var result = provider.synchronize();

        assertEquals(1, result.repositories());
        assertEquals(0, result.changeRequests());
        gitLab.verify(getRequestedFor(urlPathEqualTo("/api/v4/projects/10/merge_requests/7")));
        verify(sync, times(0)).synchronize(any(ChangeRequestSync.class));
    }

    @Test
    void isUnavailableWithoutServiceToken() {
        final var withoutToken = new GitLabConfiguration("http://localhost", "client", "secret",
                "http://localhost/callback", 600_000, 60_000, 30_000);
        assertFalse(new GitLabRepositorySyncProvider(withoutToken, (RepositorySyncConfiguration) null, client, importer).available());
        assertFalse(new GitLabRepositorySyncProvider(null, (RepositorySyncConfiguration) null, client, importer).available());
    }
}
