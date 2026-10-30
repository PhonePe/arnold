package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.DiscussionLineRange;
import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testGetMergeRequest_shouldPreserveHttpStatus_whenGitLabReturns404() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12"))
                .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"404 Not found\"}")));

        final var error = assertThrows(ArnoldError.class, () -> client.getMergeRequest(10, 12, "token"));

        assertEquals(404, error.getContext().get("httpStatus"));
        gitLab.verify(1, getRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12")));
    }

    @Test
    void testTriggerSmartDiff_shouldSendClioPipelineVariables_whenConfigured() {
        final var configuration = new GitLabConfiguration("http://localhost:" + gitLab.port(), "client-id",
                "client-secret", "http://localhost/callback", 600_000, 60_000, 30_000,
                new ClioPipelineConfiguration(99L, "main", "trigger-secret"));
        final var configuredClient = new GitLabClientImpl(configuration, new ObjectMapper());
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/99/trigger/pipeline"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        configuredClient.triggerSmartDiff("https://gitlab.example/team/repo/-/merge_requests/12", 10L, 12L,
                "a".repeat(40), "123e4567-e89b-42d3-a456-426614174000");

        gitLab.verify(postRequestedFor(urlEqualTo("/api/v4/projects/99/trigger/pipeline"))
                .withRequestBody(containing("token=trigger-secret"))
                .withRequestBody(containing("ref=main"))
                .withRequestBody(containing("variables%5BWORKFLOW%5D=smart_diff"))
                .withRequestBody(containing("variables%5BPROJECT_ID%5D=10"))
                .withRequestBody(containing("variables%5BMR_IID%5D=12"))
                .withRequestBody(containing("variables%5BEXPECTED_HEAD_SHA%5D=" + "a".repeat(40)))
                .withRequestBody(containing("variables%5BEXECUTION_ID%5D=123e4567-e89b-42d3-a456-426614174000")));
    }

    @Test
    void testResolveMergeRequest_shouldMapGitLabResponse_whenUrlIsValid() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/team%2Frepo/merge_requests/12"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"id":100,"project_id":10,"iid":12,"title":"Title","description":"Description",
                        "web_url":"http://localhost/team/repo/-/merge_requests/12","source_branch":"feature",
                        "target_branch":"main","sha":"abc","state":"opened","detailed_merge_status":"mergeable",
                        "draft":false,"author":{"id":1,"username":"alice","name":"Alice"},
                        "assignees":[{"id":2,"username":"bob","name":"Bob"}],
                        "reviewers":[{"id":3,"username":"carol","name":"Carol"}],
                        "labels":["backend"],"updated_at":"2026-07-20T10:00:00Z"}
                        """)));

        final var result = client.resolveMergeRequest(
                "http://localhost:" + gitLab.port() + "/team/repo/-/merge_requests/12", "access-token");

        assertEquals("GITLAB", result.provider());
        assertEquals("10", result.repositoryId());
        assertEquals("team/repo", result.repositoryPath());
        assertEquals("mergeable", result.mergeStatus());
        assertEquals("Alice", result.authorName());
        assertEquals(List.of("Bob"), result.assigneeNames());
        assertEquals(List.of("Carol"), result.reviewerNames());
        gitLab.verify(getRequestedFor(urlEqualTo("/api/v4/projects/team%2Frepo/merge_requests/12"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
    }

    @Test
    void testResolveMergeRequest_shouldRejectUrl_whenHostDoesNotMatchConfiguration() {
        assertThrows(ArnoldError.class, () -> client.resolveMergeRequest(
                "https://example.com/team/repo/-/merge_requests/12", "access-token"));
    }

    @Test
    void testAddReviewer_shouldResolveUsernameAndUpdateGitLab() {
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/users"))
                .withQueryParam("username", equalTo("carol"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":3,\"username\":\"carol\",\"name\":\"Carol\"}]")));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"id":100,"project_id":10,"iid":12,"title":"Title",
                        "web_url":"http://localhost/team/repo/-/merge_requests/12","state":"opened",
                        "reviewers":[{"id":2,"username":"bob","name":"Bob"}],
                        "updated_at":"2026-07-20T10:00:00Z"}
                        """)));
        gitLab.stubFor(put(urlEqualTo("/api/v4/projects/10/merge_requests/12"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"id":100,"project_id":10,"iid":12,"title":"Title",
                        "web_url":"http://localhost/team/repo/-/merge_requests/12","state":"opened",
                        "reviewers":[{"id":2,"username":"bob","name":"Bob"},
                        {"id":3,"username":"carol","name":"Carol"}],
                        "updated_at":"2026-07-20T10:01:00Z"}
                        """)));

        final var result = client.addReviewer(10, 12, "@carol", "access-token");

        assertEquals(List.of(2L, 3L), result.reviewerIds());
        gitLab.verify(putRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12"))
                .withRequestBody(matchingJsonPath("$.reviewer_ids[0]", equalTo("2")))
                .withRequestBody(matchingJsonPath("$.reviewer_ids[1]", equalTo("3"))));
    }

    @Test
    void testSearchReviewers_shouldReturnProjectMembers() {
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects/10/members/all"))
                .withQueryParam("query", equalTo("car"))
                .withQueryParam("per_page", equalTo("20"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":3,\"username\":\"carol\",\"name\":\"Carol\"}]")));

        final var reviewers = client.searchReviewers(10, "car", "access-token");

        assertEquals("carol", reviewers.get(0).username());
        assertEquals("Carol", reviewers.get(0).name());
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
                .withQueryParam("non_archived", equalTo("true"))
                .withQueryParam("per_page", equalTo("100")));
        gitLab.verify(getRequestedFor(urlPathEqualTo("/api/v4/merge_requests"))
                .withQueryParam("non_archived", equalTo("true"))
                .withQueryParam("page", equalTo("2")));
    }

    @Test
    void testIsProjectArchived_shouldReadGitLabFlagAndRejectMissingFlag() {
        for (final var archived : List.of(true, false)) {
            gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10"))
                    .willReturn(aResponse().withHeader("Content-Type", "application/json")
                            .withBody("{\"archived\":" + archived + "}")));
            assertEquals(archived, client.isProjectArchived(10, "access-token"));
        }
        gitLab.verify(getRequestedFor(urlEqualTo("/api/v4/projects/10"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));
        assertThrows(ArnoldError.class, () -> client.isProjectArchived(10, "access-token"));
    }

    @Test
    void testGetMergeRequestDiff_shouldReturnRawDiff_whenGitLabResponds() {
        final var diff = """
                diff --git a/example.txt b/example.txt
                --- a/example.txt
                +++ b/example.txt
                @@ -1 +1 @@
                -before
                +after
                """;
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/raw_diffs"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody(diff)));

        final var result = client.getMergeRequestDiff(10L, 12L, "access-token");

        assertEquals(diff, result);
        gitLab.verify(getRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12/raw_diffs"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
    }

    @Test
    void testListMergeRequestDiffVersions_shouldIncludeCommitTitle() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/versions?per_page=100"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"id":4,"created_at":"2026-07-20T10:00:00Z","base_commit_sha":"base",
                          "start_commit_sha":"start","head_commit_sha":"head"}]
                        """)));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/commits?per_page=100"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"id":"head","title":"Fix parser edge case"}]
                        """)));

        final var result = client.listMergeRequestDiffVersions(10L, 12L, "access-token");

        assertEquals("Fix parser edge case", result.get(0).commitTitle());
    }

    @Test
    void testGetMergeRequestFiles_shouldReturnBaseAndHeadContent_whenPathsAreValid() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/versions?per_page=1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"base_commit_sha":"base-sha","start_commit_sha":"start-sha","head_commit_sha":"head-sha"}]
                        """)));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/repository/files/src%2FOld.java/raw?ref=base-sha"))
                .willReturn(aResponse().withStatus(200).withBody("old content")));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/repository/files/src%2FNew.java/raw?ref=head-sha"))
                .willReturn(aResponse().withStatus(200).withBody("new content")));

        final var result = client.getMergeRequestFiles(10L, 12L, "src/Old.java", "src/New.java", null, null,
                "access-token");

        assertEquals("old content", result.oldContent());
        assertEquals("new content", result.newContent());
    }

    @Test
    void testListDiscussions_shouldMapHumanNotes_whenGitLabReturnsThreads() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/discussions?per_page=100&page=1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"id":"thread-1","notes":[
                          {"id":41,"body":"Looks good","system":false,"resolvable":true,"resolved":false,
                           "author":{"id":7,"username":"alice"},"created_at":"2026-07-20T10:00:00Z",
                           "updated_at":"2026-07-20T10:01:00Z","position":{"head_sha":"head","old_path":"a.txt",
                           "new_path":"a.txt","new_line":4}},
                          {"id":42,"body":"changed title","system":true,"updated_at":"2026-07-20T10:02:00Z"}
                        ]}]
                        """)));

        final var result = client.listDiscussions(10L, 12L, "access-token");

        assertEquals(2, result.size());
        assertEquals("thread-1", result.get(0).threadId());
        assertEquals("alice", result.get(0).authorUsername());
        assertEquals(4L, result.get(0).position().newLine());
        assertEquals("system", result.get(1).state());
        assertTrue(result.get(1).individualNote());
    }

    @Test
    void testGetMergeRequestReview_shouldMapChecksAndReviewerApprovals() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"detailed_merge_status":"discussions_not_resolved","has_conflicts":false,
                         "blocking_discussions_resolved":false,"head_pipeline":{"status":"success",
                         "web_url":"https://gitlab.example/pipelines/9"},
                         "reviewers":[{"id":3,"username":"carol","name":"Carol","avatar_url":"avatar"}]}
                        """)));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/approvals"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"approvals_required":2,"approvals_left":1,"approved":false,
                         "approved_by":[{"user":{"id":3,"username":"carol","name":"Carol"}}]}
                        """)));

        final var result = client.getMergeRequestReview(10L, 12L, "access-token");

        assertEquals("success", result.pipelineStatus());
        assertEquals(2, result.approvalsRequired());
        assertEquals("approved", result.reviewers().get(0).status());
        assertFalse(result.discussionsResolved());
    }

    @Test
    void testCreateDraft_shouldUseLatestDiffRefs_whenCommentIsInline() {
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10/merge_requests/12/versions?per_page=1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        [{"base_commit_sha":"base","start_commit_sha":"start","head_commit_sha":"head"}]
                        """)));
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/10/merge_requests/12/draft_notes"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"id":51,"note":"Please rename","author_id":7,
                         "created_at":"2026-07-20T10:00:00Z","updated_at":"2026-07-20T10:00:00Z",
                         "position":{"head_sha":"head","old_path":"a.txt","new_path":"a.txt","new_line":4}}
                        """)));
        final var request = CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("Please rename").clientOperationId("operation-1")
                .position(new DiscussionPosition(null, null, null, "a.txt", "a.txt", null, 4L, null)).build();

        final var result = client.createDraft(request, "access-token", "alice");

        assertEquals(51L, result.draftId());
        assertEquals(7L, result.authorId());
        assertEquals("alice", result.authorUsername());
        gitLab.verify(postRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12/draft_notes"))
                .withRequestBody(matchingJsonPath("$.note", equalTo("Please rename\n\n<!-- arnold -->")))
                .withRequestBody(matchingJsonPath("$.position.head_sha", equalTo("head")))
                .withRequestBody(matchingJsonPath("$.position.new_line", equalTo("4"))));
    }

    @Test
    void testCreateDraft_shouldOmitNestedLineNumbers_whenCommentHasLineRange() {
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/10/merge_requests/12/draft_notes"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"id":52,"note":"Replace this","author":{"id":7,"username":"alice"},
                         "created_at":"2026-07-20T10:00:00Z","updated_at":"2026-07-20T10:00:00Z"}
                        """)));
        final var position = new DiscussionPosition("base", "start", "old-head", "src/A.java", "src/A.java",
                null, 6L, new DiscussionLineRange("new", null, 4L, null, 6L));
        final var request = CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("Replace this").clientOperationId("operation-range")
                .position(position).build();

        client.createDraft(request, "access-token", "alice");

        gitLab.verify(postRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12/draft_notes"))
                .withRequestBody(matchingJsonPath("$.position.head_sha", equalTo("old-head")))
                .withRequestBody(matchingJsonPath("$.position.line_range.start.line_code", equalTo("01142b20d1bf46e5dab4d0fcac2c3d54f43dd924__4")))
                .withRequestBody(matchingJsonPath("$.position.line_range.start.new_line", absent()))
                .withRequestBody(matchingJsonPath("$.position.line_range.end.new_line", absent())));
    }

    @Test
    void testCreateDraft_shouldIncludeGitLabErrorBody_whenRequestIsRejected() {
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/10/merge_requests/12/draft_notes"))
                .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"Position is invalid\"}")));
        final var request = CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("Please rename").clientOperationId("operation-1").build();

        final var error = assertThrows(ArnoldError.class, () -> client.createDraft(request, "access-token", "alice"));

        assertTrue(error.getMessage().contains("Position is invalid"));
    }

    @Test
    void testCreateDiscussion_shouldPublishReplyImmediately_whenDiscussionIdIsPresent() {
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/10/merge_requests/12/discussions/thread-1/notes"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"id":61,"body":"Fixed\\n\\n<!-- arnold -->","author":{"id":7,"username":"alice"},"system":false,
                         "created_at":"2026-07-20T10:00:00Z","updated_at":"2026-07-20T10:00:00Z"}
                        """)));
        final var request = CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("Fixed").clientOperationId("operation-2")
                .discussionId("thread-1").build();

        final var result = client.createDiscussion(request, "access-token");

        assertEquals(61L, result.numericId());
        assertEquals("thread-1", result.threadId());
        gitLab.verify(postRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12/discussions/thread-1/notes"))
                .withRequestBody(matchingJsonPath("$.body", equalTo("Fixed\n\n<!-- arnold -->"))));
        assertEquals("Fixed", result.body());
    }

    @Test
    void testCreateDiscussion_shouldPublishIndividualNote_whenCommentIsTopLevel() {
        gitLab.stubFor(post(urlEqualTo("/api/v4/projects/10/merge_requests/12/notes"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("""
                        {"id":71,"body":"General feedback","author":{"id":7,"username":"alice"},"system":false,
                         "created_at":"2026-07-20T10:00:00Z","updated_at":"2026-07-20T10:00:00Z"}
                        """)));
        final var request = CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("General feedback").clientOperationId("operation-3").build();

        final var result = client.createDiscussion(request, "access-token");

        assertTrue(result.individualNote());
        assertNull(result.threadId());
        gitLab.verify(postRequestedFor(urlEqualTo("/api/v4/projects/10/merge_requests/12/notes"))
                .withRequestBody(matchingJsonPath("$.body", equalTo("General feedback\n\n<!-- arnold -->"))));
    }
}
