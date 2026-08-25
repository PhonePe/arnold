package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabClientImplTest {
    private WireMockServer gitLab;
    private GitLabClientImpl client;

    @BeforeEach
    void setUp() {
        gitLab = new WireMockServer(0);
        gitLab.start();
        final var configuration = new GitLabConfiguration("http://localhost:" + gitLab.port(), "client-id",
                "client-secret", "http://localhost/callback", 600_000, 60_000, 30_000);
        client = new GitLabClientImpl(configuration, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        gitLab.stop();
    }

    @Test
    void testResolveMergeRequest_shouldMapGitLabResponse_whenUrlIsValid() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/team%2Frepo/merge_requests/12"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"id":100,"project_id":10,"iid":12,"title":"Title","description":"Description",
                        "web_url":"http://localhost/team/repo/-/merge_requests/12","source_branch":"feature",
                        "target_branch":"main","sha":"abc","state":"opened","detailed_merge_status":"mergeable",
                        "draft":false,"author":{"id":1},"assignees":[{"id":2}],"reviewers":[{"id":3}],
                        "labels":["backend"],"updated_at":"2026-07-20T10:00:00Z"}
                        """)));

        final var result = client.resolveMergeRequest(
                "http://localhost:" + gitLab.port() + "/team/repo/-/merge_requests/12", "access-token");

        assertEquals("GITLAB", result.provider());
        assertEquals("10", result.repositoryId());
        assertEquals("team/repo", result.repositoryPath());
        assertEquals("mergeable", result.mergeStatus());
        gitLab.verify(getRequestedFor(urlEqualTo("/api/v4/projects/team%2Frepo/merge_requests/12"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
    }

    @Test
    void testResolveMergeRequest_shouldRejectUrl_whenHostDoesNotMatchConfiguration() {
        assertThrows(ArnoldError.class, () -> client.resolveMergeRequest(
                "https://example.com/team/repo/-/merge_requests/12", "access-token"));
    }

    @Test
    void testListMergeRequests_shouldFollowGitLabPagination() {
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/merge_requests")).atPriority(5)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withHeader("X-Next-Page", "2").withBody("""
                                [{"id":100,"project_id":10,"iid":12,"title":"First",
                                "web_url":"http://localhost/team/repo/-/merge_requests/12",
                                "state":"opened","updated_at":"2026-07-20T10:00:00Z"}]
                                """)));
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/merge_requests")).atPriority(1)
                .withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withHeader("X-Next-Page", "").withBody("""
                                [{"id":101,"project_id":10,"iid":13,"title":"Second",
                                "web_url":"http://localhost/team/repo/-/merge_requests/13",
                                "state":"opened","updated_at":"2026-07-20T11:00:00Z"}]
                                """)));

        final var result = client.listMergeRequests(7, MergeRequestRole.REVIEWER, "opened", null,
                "access-token");

        assertEquals(2, result.size());
        assertEquals(12, result.get(0).number());
        assertEquals(13, result.get(1).number());
        gitLab.verify(getRequestedFor(urlPathEqualTo("/api/v4/merge_requests"))
                .withQueryParam("reviewer_id", equalTo("7"))
                .withQueryParam("per_page", equalTo("100")));
        gitLab.verify(getRequestedFor(urlPathEqualTo("/api/v4/merge_requests"))
                .withQueryParam("page", equalTo("2")));
    }
}
