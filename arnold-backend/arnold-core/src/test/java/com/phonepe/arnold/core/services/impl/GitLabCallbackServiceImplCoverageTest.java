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

package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.comment.CommentRecord;
import com.phonepe.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.UserSyncControlService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.arnold.models.callback.GitLabWebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GitLabCallbackServiceImplCoverageTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionControlSyncService sync = mock(VersionControlSyncService.class);
    private final ChangeRequestService changes = mock(ChangeRequestService.class);
    private final GitLabWebhookFanoutService fanout = mock(GitLabWebhookFanoutService.class);
    private final AerospikeWebhookDeliveryStore deliveries = mock(AerospikeWebhookDeliveryStore.class);
    private final AerospikeCommentStore comments = mock(AerospikeCommentStore.class);
    private final GitLabClient client = mock(GitLabClient.class);
    private final GitLabOAuthService oauth = mock(GitLabOAuthService.class);
    private final UserMergeRequestService associations = mock(UserMergeRequestService.class);
    private final AnalysisOrchestrator orchestrator = mock(AnalysisOrchestrator.class);
    private final UserSyncControlService syncControl = mock(UserSyncControlService.class);
    private final Map<String, WebhookDelivery> records = new HashMap<>();
    private GitLabCallbackServiceImpl service;
    private int counter;

    @BeforeEach
    void setUp() {
        when(deliveries.getWithVersion(anyString())).thenAnswer(call ->
                Optional.ofNullable(records.get(call.getArgument(0))).map(value -> Versioned.of(value, 1L)));
        doAnswer(call -> {
            final WebhookDelivery delivery = call.getArgument(0);
            records.put(delivery.id(), delivery);
            return null;
        }).when(deliveries).create(any(WebhookDelivery.class));
        doAnswer(call -> {
            final WebhookDelivery delivery = call.getArgument(0);
            records.put(delivery.id(), delivery);
            return null;
        }).when(deliveries).checkAndUpdate(any(WebhookDelivery.class), anyLong());
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(true);
        service = new GitLabCallbackServiceImpl(new GitLabSyncConfiguration("secret"), deliveries, sync, changes,
                fanout, syncControl, comments, client, oauth, associations, orchestrator);
    }

    private void process(final String json) throws Exception {
        service.process("secret", "d" + counter++, "hook", mapper.readValue(json, GitLabWebhookRequest.class));
    }

    private ArnoldErrorCode failure(final String json) {
        return assertThrows(ArnoldError.class, () -> process(json)).getErrorCode();
    }

    private ChangeRequestSync capturedSync() {
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, atLeastOnce()).synchronize(captor.capture());
        return captor.getValue();
    }

    private static ChangeRequestRecord record(final String state) {
        return new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100", 12,
                "acme/test", "Feature", "Description", "https://gitlab.test/mr/12", "feature", "main", "sha", state,
                "not_approved", false, 1L, "Author", List.of(3L), List.of("Assignee"), List.of(2L), List.of("Reviewer"),
                List.of("approved"), List.of("backend"), 10, 30, 40, false, true, 20, 30, false);
    }

    @Test
    void testProcess_shouldRejectBadTokenBlankDeliveryAndUnknownEvent() throws Exception {
        final var request = mapper.readValue("{\"object_kind\":\"merge_request\"}", GitLabWebhookRequest.class);
        assertEquals(ArnoldErrorCode.AUTH_ERROR,
                assertThrows(ArnoldError.class, () -> service.process("wrong", "d", "hook", request)).getErrorCode());
        assertEquals(ArnoldErrorCode.AUTH_ERROR,
                assertThrows(ArnoldError.class, () -> service.process(null, "d", "hook", request)).getErrorCode());
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR,
                assertThrows(ArnoldError.class, () -> service.process("secret", " ", "hook", request)).getErrorCode());
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure("{\"object_kind\":\"push\"}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure("{}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR,
                assertThrows(ArnoldError.class, () -> service.process("secret", "n", "hook", null)).getErrorCode());
    }

    @Test
    void testProcess_shouldReportDuplicateAndNotifySyncControl() throws Exception {
        process("{\"object_kind\":\"pipeline\"}");
        verify(syncControl).callbackReceived();
        final var response = service.process("secret", "d0", "hook",
                mapper.readValue("{\"object_kind\":\"pipeline\"}", GitLabWebhookRequest.class));
        assertEquals("duplicate", response.status());
    }

    @Test
    void testProcessMergeRequest_shouldRejectMissingProjectOrInvalidIds() {
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure("{\"object_kind\":\"merge_request\",\"project\":{\"id\":1}}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR,
                failure("{\"object_kind\":\"merge_request\",\"object_attributes\":{\"iid\":1}}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure(
                "{\"object_kind\":\"merge_request\",\"project\":{\"id\":0},\"object_attributes\":{\"iid\":1}}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure(
                "{\"object_kind\":\"merge_request\",\"project\":{\"id\":1},\"object_attributes\":{\"iid\":0}}"));
    }

    @Test
    void testProcessMergeRequest_shouldMapFullPayloadAndDraftTitle() throws Exception {
        process("""
                {"object_kind":"merge_request","project":{"id":10,"path_with_namespace":"acme/test"},
                 "user":{"id":1,"username":"author","name":" "},
                 "assignees":[{"id":4,"username":"assignee","name":"As Signee"}],
                 "reviewers":[{"id":2,"username":"rev","state":"approved"},{"id":5,"username":"rev2"}],
                 "labels":[{"title":"backend"}],
                 "object_attributes":{"id":100,"iid":12,"author_id":1,"title":"Draft: thing","description":"d",
                   "url":"https://gitlab/mr/12","state":"opened","merge_status":"cannot_be_merged",
                   "source_branch":"f","target_branch":"main","last_commit":{"id":"abc"},
                   "has_conflicts":true,"blocking_discussions_resolved":true,
                   "updated_at":"2026-09-16T10:00:00Z","created_at":"bad-date"}}
                """);
        final var current = capturedSync().changeRequest();
        assertTrue(current.draft());
        assertEquals("abc", current.headSha());
        assertEquals("author", current.authorName());
        assertEquals(List.of("As Signee"), current.assigneeNames());
        assertEquals(List.of("approved", "unreviewed"), current.reviewerStates());
        assertTrue(current.hasConflicts());
        assertEquals(ChangeRequestSync.Action.UPSERT, capturedSync().action());
    }

    @Test
    void testProcessMergeRequest_shouldUseMergedAndClosedActions_andReopen() throws Exception {
        process("""
                {"object_kind":"merge_request","project":{"id":10},"object_attributes":{"id":1,"iid":12,
                 "state":"merged","merged_at":"2026-09-16 10:00:00 UTC","updated_at":"2026-09-16 10:00:00 UTC"}}
                """);
        assertEquals(ChangeRequestSync.Action.MERGED, capturedSync().action());
        assertTrue(capturedSync().changeRequest().mergedAt() > 0);
        process("""
                {"object_kind":"merge_request","project":{"id":10},"object_attributes":{"id":1,"iid":12,
                 "state":"closed","updated_at":"2026-09-16 10:00:00 UTC"}}
                """);
        assertEquals(ChangeRequestSync.Action.CLOSED, capturedSync().action());
        when(changes.record("gitlab:10:12")).thenReturn(Optional.of(record("closed")));
        process("""
                {"object_kind":"merge_request","project":{"id":10},"object_attributes":{"id":1,"iid":12,
                 "state":"opened","title":"[WIP] x","updated_at":"2026-09-16 10:00:00 UTC"}}
                """);
        assertEquals(ChangeRequestSync.Action.REOPENED, capturedSync().action());
    }

    @Test
    void testProcessMergeRequest_shouldKeepPreviousDescription_andOverrideWhenChanged() throws Exception {
        when(changes.record("gitlab:10:12")).thenReturn(Optional.of(record("opened")));
        process("""
                {"object_kind":"merge_request","project":{"id":10},"object_attributes":{"id":1,"iid":12}}
                """);
        assertEquals("Description", capturedSync().changeRequest().description());
        process("""
                {"object_kind":"merge_request","project":{"id":10},"changes":{"description":{"current":"x"}},
                 "object_attributes":{"id":1,"iid":12}}
                """);
        assertNull(capturedSync().changeRequest().description());
    }

    @Test
    void testProcessMergeRequest_shouldUseReviewerFallbacks() throws Exception {
        when(changes.record("gitlab:10:12")).thenReturn(Optional.of(record("opened")));
        process("""
                {"object_kind":"merge_request","project":{"id":10},
                 "reviewers":[{"id":2,"username":"rev"},{"id":9,"username":"new"}],
                 "changes":{"reviewers":{"current":[{"id":9,"state":"requested_changes"}]}},
                 "object_attributes":{"id":1,"iid":12}}
                """);
        assertEquals(List.of("approved", "requested_changes"), capturedSync().changeRequest().reviewerStates());
    }

    @Test
    void testProcessPipeline_shouldNotifyOrchestratorAndSkipWithoutMergeRequest() throws Exception {
        process("{\"object_kind\":\"pipeline\",\"object_attributes\":{\"id\":5,\"status\":\"success\"}}");
        verify(orchestrator).onPipelineFinished(5, "success");
        verifyNoInteractions(sync);
    }

    @Test
    void testProcessPipeline_shouldValidateMergeRequestAndIgnoreUnknownRecord() throws Exception {
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure(
                "{\"object_kind\":\"pipeline\",\"project\":{\"id\":1},\"merge_request\":{\"iid\":0}}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR,
                failure("{\"object_kind\":\"pipeline\",\"merge_request\":{\"iid\":1}}"));
        process("{\"object_kind\":\"pipeline\",\"project\":{\"id\":10},\"merge_request\":{\"iid\":12}}");
        verifyNoInteractions(sync);
    }

    @Test
    void testProcessPipeline_shouldRethrowLastFailure_whenNoCredentialWorks() throws Exception {
        when(changes.record("gitlab:10:12")).thenReturn(Optional.of(record("opened")));
        when(associations.associationsForMergeRequest("gitlab:10:12")).thenReturn(List.of(
                new UserChangeRequest("a", "user", "gitlab:10:12", Set.of(), true, true, 1, 2, 20),
                new UserChangeRequest("b", "gone", "gitlab:10:12", Set.of(), false, true, 1, 2, 20)));
        when(oauth.validCredential("user")).thenThrow(new IllegalStateException("expired"));
        final var body = "{\"object_kind\":\"pipeline\",\"project\":{\"id\":10},\"merge_request\":{\"iid\":12}}";
        assertThrows(IllegalStateException.class, () -> process(body));
        when(associations.associationsForMergeRequest("gitlab:10:12")).thenReturn(List.of());
        process(body);
        verifyNoInteractions(sync);
    }

    @Test
    void testProcessNote_shouldIgnoreNonMergeRequestAndValidateIds() throws Exception {
        process("{\"object_kind\":\"note\",\"object_attributes\":{\"noteable_type\":\"Issue\"}}");
        verifyNoInteractions(sync);
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure(
                "{\"object_kind\":\"note\",\"object_attributes\":{\"id\":1,\"noteable_type\":\"MergeRequest\"},\"project\":{\"id\":1}}"));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, failure(
                "{\"object_kind\":\"note\",\"project\":{\"id\":1},\"merge_request\":{\"iid\":1},\"object_attributes\":{\"id\":0,\"noteable_type\":\"MergeRequest\"}}"));
    }

    @Test
    void testProcessNote_shouldMarkSystemAndDeletedNotes_andKeepPreviousAuthor() throws Exception {
        final var previous = new CommentRecord("gitlab:10:12:7", VersionControlProviderType.GITLAB, "gitlab:10:12", "disc",
                7, 1L, "oldname", "old", "published", true, false, false, false, "arnold", 5, 6, 7, null, List.of(), null);
        when(comments.get(anyString())).thenReturn(Optional.of(previous));
        process("""
                {"object_kind":"note","project":{"id":10},"merge_request":{"iid":12},
                 "object_attributes":{"id":7,"author_id":1,"noteable_type":"MergeRequest","action":"delete","system":true}}
                """);
        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(sync).synchronize(captor.capture());
        final var comment = captor.getValue();
        assertTrue(comment.deleted());
        assertEquals("system", comment.state());
        assertEquals("oldname", comment.authorUsername());
        assertEquals("old", comment.body());
        assertEquals("arnold", comment.source());
        assertEquals("disc", comment.discussionId());
    }

    @Test
    void testProcessNote_shouldUseEventUserAsAuthor_whenIdsMatch() throws Exception {
        process("""
                {"object_kind":"note","project":{"id":10},"merge_request":{"iid":12},"user":{"id":3,"username":"me"},
                 "object_attributes":{"id":8,"author_id":3,"noteable_type":"MergeRequest","note":"hi","system":false,
                  "discussion_id":"abc","resolvable":true,"resolved":true,"individual_note":true}}
                """);
        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(sync).synchronize(captor.capture());
        assertEquals("me", captor.getValue().authorUsername());
        assertEquals("published", captor.getValue().state());
        assertTrue(captor.getValue().resolved());
        assertTrue(captor.getValue().individualNote());
    }
}
