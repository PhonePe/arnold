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

package com.phonepe.arnold.core.provider.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.DiscussionLineRange;
import com.phonepe.arnold.models.comment.DiscussionPosition;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabClientImplCoverageTest {
    private static final String MR = "/api/v4/projects/10/merge_requests/12";
    private static final String MR_JSON = """
            {"id":100,"project_id":10,"iid":12,"title":"T","description":"D",
            "web_url":"http://localhost/team/repo/-/merge_requests/12","source_branch":"f","target_branch":"main",
            "sha":"abc","state":"opened","detailed_merge_status":"requested_changes","draft":true,
            "author":{"id":1,"username":"alice"},
            "assignees":[{"id":2,"username":"bob","name":" "}],
            "reviewers":[{"id":3,"username":"carol","name":"Carol","avatar_url":"http://a"}],
            "labels":["x"],"created_at":"2026-07-20T10:00:00Z","merged_at":"","closed_at":"2026-07-21T10:00:00Z",
            "has_conflicts":true,"blocking_discussions_resolved":false,
            "head_pipeline":{"status":"success","web_url":"http://p"},"updated_at":"2026-07-20T10:00:00Z"}
            """;
    private WireMockServer gitLab;
    private GitLabClientImpl client;

    @BeforeEach
    void setUp() {
        gitLab = new WireMockServer(0);
        gitLab.start();
        client = new GitLabClientImpl(new GitLabConfiguration("http://localhost:" + gitLab.port() + "/", "id", "secret",
                "http://localhost/cb", 600_000, 60_000, 5_000), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        gitLab.stop();
    }

    private void stub(final MappingBuilder builder, final int status, final String body) {
        gitLab.stubFor(builder.willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static CreateDiscussionRequest.CreateDiscussionRequestBuilder create() {
        return CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10L).mrIid(12L).body("hi").clientOperationId("op");
    }

    @Test
    void oauthTokenExchangeAndRefreshAndCurrentUser() {
        stub(post(urlEqualTo("/oauth/token")), 200,
                "{\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":60,\"scope\":\"api\",\"extra\":1}");
        stub(get(urlEqualTo("/api/v4/user")), 200, "{\"id\":5,\"username\":\"alice\"}");

        final var exchanged = client.exchangeAuthorizationCode("code");
        final var refreshed = client.refreshAccessToken("r");
        final var user = client.currentUser("tok");

        assertEquals("a", exchanged.accessToken());
        assertEquals("r", refreshed.refreshToken());
        assertTrue(exchanged.expiresAt() > System.currentTimeMillis());
        assertEquals(5L, user.id());
        gitLab.verify(postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(containing("grant_type=refresh_token")));
        gitLab.verify(postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(containing("code=code")));
    }

    @Test
    void getMergeRequestMapsAllFields() {
        stub(get(urlEqualTo(MR)), 200, MR_JSON);

        final var record = client.getMergeRequest(10, 12, "tok");

        assertEquals("team/repo", record.repositoryPath());
        assertEquals("requested_changes", record.mergeStatus());
        assertEquals(List.of("bob"), record.assigneeNames());
        assertEquals(List.of("Carol"), record.reviewerNames());
        assertFalse(record.blockingDiscussionsResolved());
        assertEquals(0L, record.mergedAt());
        assertTrue(record.closedAt() > 0);
        assertTrue(record.hasConflicts());
    }

    @Test
    void getMergeRequestFailsOnInvalidWebUrl() {
        stub(get(urlEqualTo(MR)), 200, MR_JSON.replace("http://localhost/team/repo/-/merge_requests/12", "http://localhost/"));
        assertThrows(ArnoldError.class, () -> client.getMergeRequest(10, 12, "tok"));
    }

    @Test
    void resolveMergeRequestRejectsMalformedUrls() {
        final var base = "http://localhost:" + gitLab.port();
        for (final var url : List.of(base + "/team/repo", base + "/team/repo/-/merge_requests/abc",
                "https://localhost:" + gitLab.port() + "/a/-/merge_requests/1", "http://localhost:1/a/-/merge_requests/1",
                "not a url")) {
            assertThrows(ArnoldError.class, () -> client.resolveMergeRequest(url, "tok"), url);
        }
    }

    @Test
    void addReviewerStripsAtSignAndRejectsUnknownUser() {
        stub(get(urlPathEqualTo("/api/v4/users")), 200, "[{\"id\":9,\"username\":\"someone\"}]");
        assertThrows(ArnoldError.class, () -> client.addReviewer(10, 12, "@carol", "tok"));
        gitLab.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/api/v4/users"))
                .withQueryParam("username", equalTo("carol")));
    }

    @Test
    void removeReviewerUpdatesRemainingReviewers() {
        stub(get(urlEqualTo(MR)), 200, MR_JSON);
        stub(put(urlEqualTo(MR)), 200, MR_JSON);

        final var result = client.removeReviewer(10, 12, 3, "tok");

        assertEquals("team/repo", result.repositoryPath());
        gitLab.verify(putRequestedFor(urlEqualTo(MR)).withRequestBody(equalTo("{\"reviewer_ids\":[]}")));
    }

    @Test
    void searchReviewersFallsBackToUsernameWhenNameBlank() {
        stub(get(urlPathEqualTo("/api/v4/projects/10/members/all")), 200,
                "[{\"id\":1,\"username\":\"u1\",\"name\":\"\"},{\"id\":2,\"username\":\"u2\",\"name\":\"Two\"}]");
        final var result = client.searchReviewers(10, "u", "tok");
        assertEquals("u1", result.get(0).name());
        assertEquals("Two", result.get(1).name());
    }

    @Test
    void isProjectArchivedReturnsFlag() {
        stub(get(urlEqualTo("/api/v4/projects/10")), 200, "{\"archived\":true}");
        assertTrue(client.isProjectArchived(10, "tok"));
    }

    @Test
    void listMergeRequestsSupportsRolesAndUpdatedAfter() {
        stub(get(urlPathEqualTo("/api/v4/merge_requests")), 200, "[" + MR_JSON + "]");

        for (final var role : MergeRequestRole.values()) {
            assertEquals(1, client.listMergeRequests(7, role, "opened", 1_000L, "tok").size());
        }
        assertEquals(1, client.listMergeRequests(7, MergeRequestRole.AUTHOR, "opened", null, "tok").size());
        gitLab.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/api/v4/merge_requests"))
                .withQueryParam("assignee_id", equalTo("7")).withQueryParam("updated_after", containing("1970-01-01")));
        gitLab.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/api/v4/merge_requests"))
                .withQueryParam("reviewer_id", equalTo("7")));
    }

    @Test
    void createTopLevelDiscussionFromBody() {
        stub(post(urlEqualTo(MR + "/discussions")), 201, """
                {"id":"d1","individual_note":true,"notes":[{"id":5,"author":{"id":1,"username":"a"},
                "body":"hi\\n\\n<!-- arnold -->","system":false,"resolvable":false,
                "created_at":"2026-07-20T10:00:00Z","suggestions":[{"id":1,"applicable":true,"applied":false,
                "from_content":"a","to_content":"b","from_line":1,"to_line":2}]}]}
                """);
        final var comment = client.createDiscussion(10, 12, "hi", "tok");
        assertEquals("d1", comment.threadId());
        assertEquals("hi", comment.body());
        assertTrue(comment.individualNote());
        assertEquals(1, comment.suggestions().size());
        assertEquals("published", comment.state());
    }

    @Test
    void createDiscussionFailsWhenGitLabReturnsNoNotes() {
        stub(post(urlEqualTo(MR + "/discussions")), 201, "{\"id\":\"d1\",\"notes\":[]}");
        assertThrows(ArnoldError.class, () -> client.createDiscussion(10, 12, "hi", "tok"));
        assertThrows(ArnoldError.class, () -> client.createDiscussion(create()
                .position(new DiscussionPosition("b", "s", "h", "a", "a", null, 1L, null)).build(), "tok"));
    }

    @Test
    void createDiscussionWithPositionCompletesRefsAndBuildsLineRange() {
        stub(get(urlEqualTo(MR + "/versions?per_page=1")), 200,
                "[{\"id\":1,\"base_commit_sha\":\"B\",\"start_commit_sha\":\"S\",\"head_commit_sha\":\"H\"}]");
        stub(post(urlEqualTo(MR + "/discussions")), 201, """
                {"id":"d2","notes":[{"id":6,"body":"x","system":true,"created_at":"2026-07-20T10:00:00Z",
                "position":{"base_sha":"B","old_path":"a","new_path":"a","old_line":3,
                "line_range":{"start":{"type":"old","old_line":3},"end":{"type":"old","old_line":4}}}}]}
                """);
        final var position = new DiscussionPosition(null, null, null, "a", "a", 3L, null,
                new DiscussionLineRange("old", 3L, null, 4L, null));

        final var comment = client.createDiscussion(create().position(position).build(), "tok");

        assertEquals("system", comment.state());
        assertTrue(comment.individualNote());
        assertEquals("old", comment.position().lineRange().side());
        gitLab.verify(postRequestedFor(urlEqualTo(MR + "/discussions"))
                .withRequestBody(matchingJsonPath("$.position.head_sha", equalTo("H")))
                .withRequestBody(matchingJsonPath("$.position.position_type", equalTo("text")))
                .withRequestBody(matchingJsonPath("$.position.line_range.end.old_line", equalTo("4"))));
    }

    @Test
    void createDiscussionFileLevelPositionAndMissingDiffVersion() {
        stub(get(urlEqualTo(MR + "/versions?per_page=1")), 200, "[]");
        final var filePosition = new DiscussionPosition(null, null, null, "a", "a", null, null, null);
        assertThrows(ArnoldError.class, () -> client.createDiscussion(create().position(filePosition).build(), "tok"));

        stub(post(urlEqualTo(MR + "/discussions")), 201,
                "{\"id\":\"d\",\"notes\":[{\"id\":1,\"body\":\"x\",\"created_at\":\"2026-07-20T10:00:00Z\"}]}");
        final var full = new DiscussionPosition("b", "s", "h", "a", "a", null, null, null);
        client.createDiscussion(create().position(full).build(), "tok");
        gitLab.verify(postRequestedFor(urlEqualTo(MR + "/discussions"))
                .withRequestBody(matchingJsonPath("$.position.position_type", equalTo("file"))));
    }

    @Test
    void listDiscussionsPaginatesUntilShortPage() {
        final var full = IntStream.range(0, 100).mapToObj(i -> "{\"id\":\"d" + i + "\",\"notes\":[]}")
                .collect(Collectors.joining(",", "[", "]"));
        stub(get(urlEqualTo(MR + "/discussions?per_page=100&page=1")), 200, full);
        stub(get(urlEqualTo(MR + "/discussions?per_page=100&page=2")), 200,
                "[{\"id\":\"x\",\"individual_note\":true,\"notes\":[{\"id\":1,\"body\":\"b\",\"resolved\":true}]},"
                        + "{\"id\":\"y\"}]");
        final var comments = client.listDiscussions(10, 12, "tok");
        assertEquals(1, comments.size());
        assertTrue(comments.get(0).resolved());
    }

    @Test
    void listDraftsPaginatesAndMapsAuthorFallbacks() {
        final var full = IntStream.range(0, 100).mapToObj(i -> "{\"id\":" + (i + 1) + ",\"note\":\"n\"}")
                .collect(Collectors.joining(",", "[", "]"));
        stub(get(urlEqualTo(MR + "/draft_notes?per_page=100&page=1")), 200, full);
        stub(get(urlEqualTo(MR + "/draft_notes?per_page=100&page=2")), 200, """
                [{"id":201,"note":"r","in_reply_to_discussion_id":"disc","author":{"id":4,"username":"zed"}},
                 {"id":202,"note":"s","discussion_id":"disc2","author_id":9,
                  "position":{"old_path":"a","new_path":"a","new_line":1}}]
                """);
        final var drafts = client.listDrafts(10, 12, "tok", "me");
        assertEquals(102, drafts.size());
        assertEquals("me", drafts.get(0).authorUsername());
        assertTrue(drafts.get(0).individualNote());
        assertEquals("disc", drafts.get(100).threadId());
        assertEquals("zed", drafts.get(100).authorUsername());
        assertFalse(drafts.get(100).individualNote());
        assertEquals("disc2", drafts.get(101).threadId());
        assertEquals(9L, drafts.get(101).authorId());
    }

    @Test
    void draftLifecycleUpdateDeletePublishAndApprove() {
        stub(put(urlEqualTo(MR + "/draft_notes/5")), 200, "{\"id\":5,\"note\":\"new\"}");
        stub(delete(urlEqualTo(MR + "/draft_notes/5")), 204, "");
        stub(post(urlEqualTo(MR + "/draft_notes/bulk_publish")), 204, "");
        stub(post(urlEqualTo(MR + "/approve")), 201, "{}");

        final var updated = client.updateDraft(5, UpdateDraftRequest.builder().projectId(10).mrIid(12).body("new").build(),
                "tok", "me");
        client.deleteDraft(5, 10, 12, "tok");
        client.publishDrafts(PublishReviewRequest.builder().projectId(10).mrIid(12).summary(" ")
                .reviewerState("approved").build(), "tok");
        client.publishDrafts(PublishReviewRequest.builder().projectId(10).mrIid(12).summary("Looks off")
                .reviewerState("requested_changes").build(), "tok");

        assertEquals("me", updated.authorUsername());
        gitLab.verify(deleteRequestedFor(urlEqualTo(MR + "/draft_notes/5")));
        gitLab.verify(1, postRequestedFor(urlEqualTo(MR + "/approve")));
        gitLab.verify(postRequestedFor(urlEqualTo(MR + "/draft_notes/bulk_publish"))
                .withRequestBody(matchingJsonPath("$.reviewer_state", equalTo("reviewed"))));
        gitLab.verify(postRequestedFor(urlEqualTo(MR + "/draft_notes/bulk_publish"))
                .withRequestBody(matchingJsonPath("$.reviewer_state", equalTo("requested_changes"))));
    }

    @Test
    void resolveDiscussionAndApplySuggestion() {
        stub(put(urlEqualTo(MR + "/discussions/a%2Fb")), 200, "{}");
        stub(put(urlEqualTo("/api/v4/suggestions/3/apply")), 200, "{}");

        client.resolveDiscussion("a/b", ResolveDiscussionRequest.builder().projectId(10).mrIid(12).resolved(true).build(),
                "tok");
        client.applySuggestion(3, " ", "tok");
        client.applySuggestion(3, "msg", "tok");

        gitLab.verify(putRequestedFor(urlEqualTo(MR + "/discussions/a%2Fb"))
                .withRequestBody(matchingJsonPath("$.resolved", equalTo("true"))));
        gitLab.verify(putRequestedFor(urlEqualTo("/api/v4/suggestions/3/apply"))
                .withRequestBody(matchingJsonPath("$.commit_message", equalTo("msg"))));
    }

    @Test
    void reviewMapsRequestedChangesForSingleReviewerAndDefaultsMissingValues() {
        stub(get(urlEqualTo(MR)), 200, MR_JSON);
        stub(get(urlEqualTo(MR + "/approvals")), 200, "{\"approved\":false}");

        final var review = client.getMergeRequestReview(10, 12, "tok");

        assertEquals("requested_changes", review.reviewers().get(0).status());
        assertEquals("success", review.pipelineStatus());
        assertEquals(0, review.approvalsRequired());
        assertTrue(review.hasConflicts());
    }

    @Test
    void reviewWithoutPipelineOrReviewers() {
        stub(get(urlEqualTo(MR)), 200, "{\"id\":1,\"project_id\":10,\"iid\":12,\"blocking_discussions_resolved\":true}");
        stub(get(urlEqualTo(MR + "/approvals")), 200,
                "{\"approved\":true,\"approvals_required\":2,\"approvals_left\":1,\"approved_by\":[{\"user\":{\"id\":3,\"username\":\"c\"}},{}]}");
        final var review = client.getMergeRequestReview(10, 12, "tok");
        assertNull(review.pipelineStatus());
        assertEquals(2, review.approvalsRequired());
        assertTrue(review.reviewers().isEmpty());
    }

    @Test
    void diffVersionPatchHandlesNewDeletedRenamedAndModifiedFiles() {
        stub(get(urlEqualTo(MR + "/versions/4")), 200, """
                {"diffs":[
                 {"old_path":"n","new_path":"n","b_mode":"100644","new_file":true,"diff":"@@ +1 @@\\n+x"},
                 {"old_path":"d","new_path":"d","a_mode":"100644","deleted_file":true,"diff":"@@ -1 @@\\n-x\\n"},
                 {"old_path":"r1","new_path":"r2","renamed_file":true,"diff":""},
                 {"old_path":"r3","new_path":"r4","renamed_file":true,"diff":"@@"},
                 {"old_path":"m","new_path":"m","diff":"@@"}]}
                """);
        final var patch = client.getMergeRequestDiffVersion(10, 12, 4, "tok");
        assertTrue(patch.contains("new file mode 100644"));
        assertTrue(patch.contains("--- /dev/null"));
        assertTrue(patch.contains("deleted file mode 100644"));
        assertTrue(patch.contains("+++ /dev/null"));
        assertTrue(patch.contains("similarity index 100%"));
        assertTrue(patch.contains("similarity index 99%"));
        assertTrue(patch.contains("rename to r4"));
        assertTrue(patch.endsWith("@@\n"));
    }

    @Test
    void diffVersionPatchWithNullDiffs() {
        stub(get(urlEqualTo(MR + "/versions/4")), 200, "{}");
        assertEquals("", client.getMergeRequestDiffVersion(10, 12, 4, "tok"));
    }

    @Test
    void filesUseLatestVersionWhenShasMissing() {
        stub(get(urlEqualTo(MR + "/versions?per_page=1")), 200,
                "[{\"id\":1,\"base_commit_sha\":\"B\",\"start_commit_sha\":\"S\",\"head_commit_sha\":\"H\"}]");
        gitLab.stubFor(get(urlPathEqualTo("/api/v4/projects/10/repository/files/src%2FA.java/raw"))
                .willReturn(aResponse().withStatus(200).withBody("content")));

        final var files = client.getMergeRequestFiles(10, 12, "src/A.java", "src/A.java", null, null, "tok");

        assertEquals("content", files.oldContent());
        gitLab.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathEqualTo("/api/v4/projects/10/repository/files/src%2FA.java/raw")).withQueryParam("ref", equalTo("B")));
    }

    @Test
    void clientErrorsWithoutBodyAndRetriesOnServerErrors() {
        stub(get(urlEqualTo("/api/v4/user")), 403, "");
        final var error = assertThrows(ArnoldError.class, () -> client.currentUser("tok"));
        assertEquals(403, error.getContext().get("httpStatus"));
        assertFalse(error.getMessage().contains(":"));

        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10")).inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("second"));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10")).inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "soon"))
                .willSetStateTo("third"));
        gitLab.stubFor(get(urlEqualTo("/api/v4/projects/10")).inScenario("retry")
                .whenScenarioStateIs("third")
                .willReturn(aResponse().withStatus(200).withBody("{\"archived\":false}")));
        assertFalse(client.isProjectArchived(10, "tok"));
    }

    @Test
    void persistentServerErrorsEventuallyFail() {
        stub(get(urlEqualTo("/api/v4/user")), 500, "{}");
        assertThrows(ArnoldError.class, () -> client.currentUser("tok"));
        gitLab.verify(3, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/api/v4/user")));
    }

    @Test
    void malformedJsonAndTextFailuresArePropagatedAsClientFailures() {
        stub(get(urlEqualTo("/api/v4/user")), 200, "not json");
        assertThrows(ArnoldError.class, () -> client.currentUser("tok"));
        stub(get(urlPathEqualTo("/api/v4/merge_requests")), 200, "not json");
        assertThrows(ArnoldError.class, () -> client.listMergeRequests(1, MergeRequestRole.AUTHOR, "opened", null, "tok"));
        gitLab.stop();
        assertThrows(ArnoldError.class, () -> client.getMergeRequestDiff(10, 12, "tok"));
        assertThrows(ArnoldError.class, () -> client.deleteDraft(1, 10, 12, "tok"));
    }
}
