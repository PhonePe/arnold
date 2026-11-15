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
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.DiscussionPosition;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubClientTest {

    private static final String PULL = """
            {"id":555,"number":7,"title":"Add cache","body":"Speeds up reads","state":"open","draft":false,
             "html_url":"%s/acme/api/pull/7","merged_at":null,"closed_at":null,
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z","mergeable_state":"clean",
             "user":{"id":1,"login":"alice"},"assignees":[{"id":2,"login":"bob"}],
             "requested_reviewers":[{"id":3,"login":"carol","avatar_url":"https://avatars/carol"}],
             "labels":[{"name":"perf"}],
             "head":{"ref":"feature","sha":"head-sha"},
             "base":{"ref":"main","sha":"base-sha","repo":{"id":42,"full_name":"acme/api"}}}""";

    private WireMockServer server;
    private GitHubClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        final var config = new GitHubConfiguration();
        config.setWebUrl(server.baseUrl());
        config.setApiUrl(server.baseUrl() + "/api/v3");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setCallbackUrl("https://arnold.example.com/github/oauth/callback");
        client = new GitHubClient(config, new ObjectMapper(), new OkHttpClient(),
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        server.stubFor(get(urlEqualTo("/api/v3/repositories/42"))
                .willReturn(aResponse().withBody("{\"full_name\":\"acme/api\"}")));
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/pulls/7"))
                .willReturn(aResponse().withBody(PULL.formatted(server.baseUrl()))));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void resolvesPullRequestUrlAndMapsFields() {
        final var pr = client.resolvePullRequest(server.baseUrl() + "/acme/api/pull/7/files", "token");

        assertEquals("github:42:7", pr.id());
        assertEquals("GITHUB", pr.provider());
        assertEquals("42", pr.repositoryId());
        assertEquals("acme/api", pr.repositoryPath());
        assertEquals("opened", pr.state());
        assertEquals("head-sha", pr.headSha());
        assertEquals(java.util.List.of(3L), pr.reviewerIds());
        assertEquals(java.util.List.of("perf"), pr.labels());
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlEqualTo("/api/v3/repos/acme/api/pulls/7")).withHeader("Authorization", equalTo("Bearer token")));
    }

    @Test
    void rejectsUrlsFromOtherHosts() {
        final var error = assertThrows(ArnoldError.class,
                () -> client.resolvePullRequest("https://evil.example.com/acme/api/pull/7", "token"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, error.getErrorCode());
    }

    @Test
    void mapsUnauthorizedToAuthError() {
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/pulls/8")).willReturn(aResponse().withStatus(401)));

        final var error = assertThrows(ArnoldError.class,
                () -> client.resolvePullRequest(server.baseUrl() + "/acme/api/pull/8", "expired"));
        assertEquals(ArnoldErrorCode.AUTH_ERROR, error.getErrorCode());
    }

    @Test
    void reviewCombinesReviewsRequestedReviewersAndChecks() {
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/pulls/7/reviews?per_page=100")).willReturn(aResponse()
                .withBody("""
                        [{"id":1,"state":"COMMENTED","user":{"id":4,"login":"dave"}},
                         {"id":2,"state":"APPROVED","user":{"id":4,"login":"dave"}},
                         {"id":3,"state":"PENDING","user":{"id":5,"login":"erin"}}]""")));
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/commits/head-sha/status"))
                .willReturn(aResponse().withBody("{\"state\":\"success\",\"total_count\":1}")));
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/commits/head-sha/check-runs?per_page=100"))
                .willReturn(aResponse().withBody("{\"check_runs\":[{\"status\":\"in_progress\"}]}")));

        final var review = client.review(42, 7, "token");

        assertTrue(review.approved());
        assertEquals("running", review.pipelineStatus());
        assertEquals(2, review.reviewers().size());
        assertEquals("pending", review.reviewers().get(0).status());
        assertEquals("approved", review.reviewers().get(1).status());
    }

    @Test
    void listsIssueCommentsAndReviewThreadsWithoutPendingDrafts() {
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/issues/7/comments?per_page=100")).willReturn(aResponse()
                .withBody("[{\"id\":10,\"body\":\"LGTM\",\"user\":{\"id\":1,\"login\":\"alice\"},"
                        + "\"created_at\":\"2026-01-01T00:00:00Z\",\"updated_at\":\"2026-01-01T00:00:00Z\"}]")));
        server.stubFor(post(urlEqualTo("/api/graphql")).willReturn(aResponse().withBody("""
                {"data":{"repository":{"pullRequest":{"reviewThreads":{"pageInfo":{"hasNextPage":false},"nodes":[
                  {"id":"T1","isResolved":true,"path":"a.java","line":12,"diffSide":"RIGHT","comments":{"nodes":[
                    {"databaseId":20,"body":"nit","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z",
                     "author":{"login":"bob","databaseId":2},"pullRequestReview":{"state":"COMMENTED"}},
                    {"databaseId":21,"body":"draft","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z",
                     "author":{"login":"bob","databaseId":2},"pullRequestReview":{"state":"PENDING"}}]}}]}}}}}""")));

        final var comments = client.discussions(42, 7, "token");

        assertEquals(2, comments.size());
        assertEquals("issue:10", comments.get(0).threadId());
        assertFalse(comments.get(0).resolvable());
        assertEquals("T1", comments.get(1).threadId());
        assertTrue(comments.get(1).resolved());
        assertEquals(12L, comments.get(1).position().newLine());
    }

    @Test
    void createsLineCommentOnTheRightSide() {
        server.stubFor(post(urlEqualTo("/api/v3/repos/acme/api/pulls/7/comments")).willReturn(aResponse()
                .withStatus(201).withBody("{\"id\":30,\"body\":\"x\",\"path\":\"a.java\",\"line\":5,\"side\":\"RIGHT\","
                        + "\"user\":{\"id\":1,\"login\":\"alice\"},\"created_at\":\"2026-01-01T00:00:00Z\","
                        + "\"updated_at\":\"2026-01-01T00:00:00Z\"}")));
        final var request = CreateDiscussionRequest.builder()
                .provider(CreateDiscussionRequest.VersionControlProvider.GITHUB).projectId(42).mrIid(7).body("x")
                .clientOperationId("op").position(new DiscussionPosition("b", "s", "head-sha", "a.java", "a.java",
                        null, 5L, null)).build();

        final var comment = client.createDiscussion(request, "token");

        assertEquals(30, comment.numericId());
        server.verify(postRequestedFor(urlEqualTo("/api/v3/repos/acme/api/pulls/7/comments")).withRequestBody(
                equalToJson("{\"body\":\"x\",\"commit_id\":\"head-sha\",\"path\":\"a.java\",\"line\":5,\"side\":\"RIGHT\"}")));
    }

    @Test
    void publishingSubmitsThePendingReview() {
        server.stubFor(get(urlEqualTo("/api/v3/repos/acme/api/pulls/7/reviews?per_page=100")).willReturn(aResponse()
                .withBody("[{\"id\":99,\"node_id\":\"R99\",\"state\":\"PENDING\",\"user\":{\"login\":\"Alice\"}}]")));
        server.stubFor(post(urlEqualTo("/api/v3/repos/acme/api/pulls/7/reviews/99/events"))
                .willReturn(aResponse().withBody("{}")));

        client.publishReview(PublishReviewRequest.builder().projectId(42).mrIid(7).reviewerState("approved")
                .summary("Ship it").build(), "token", "alice");

        server.verify(postRequestedFor(urlEqualTo("/api/v3/repos/acme/api/pulls/7/reviews/99/events"))
                .withRequestBody(equalToJson("{\"event\":\"APPROVE\",\"body\":\"Ship it\"}")));
    }

    @Test
    void resolvesThreadsThroughGraphql() {
        server.stubFor(post(urlEqualTo("/api/graphql")).willReturn(aResponse().withBody("{\"data\":{}}")));

        client.resolveThread("T1", true, "token");

        server.verify(postRequestedFor(urlEqualTo("/api/graphql")).withRequestBody(containing("resolveReviewThread"))
                .withRequestBody(containing("\"thread\":\"T1\"")));
        assertThrows(ArnoldError.class, () -> client.resolveThread("issue:10", true, "token"));
    }

    @Test
    void exchangesCodeForExpiringUserToken() {
        server.stubFor(post(urlPathEqualTo("/login/oauth/access_token")).willReturn(aResponse().withBody(
                "{\"access_token\":\"ghu_x\",\"refresh_token\":\"ghr_y\",\"expires_in\":28800,\"scope\":\"\"}")));

        final var token = client.exchangeCode("code");

        assertEquals("ghu_x", token.accessToken());
        assertEquals("ghr_y", token.refreshToken());
        assertEquals(1_000 + 28_800_000L, token.expiresAt());
        server.stubFor(post(urlPathEqualTo("/login/oauth/access_token")).willReturn(aResponse().withBody(
                "{\"error\":\"bad_verification_code\"}")));
        assertThrows(ArnoldError.class, () -> client.exchangeCode("bad"));
    }
}
