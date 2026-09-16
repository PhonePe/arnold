package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.devx.arnold.core.model.vcs.*;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.services.*;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.devx.arnold.models.callback.GitLabWebhookRequest;
import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import com.phonepe.devx.arnold.models.comment.SuggestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitLabCallbackServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final VersionControlSyncService sync = mock(VersionControlSyncService.class);
    private final ChangeRequestService changes = mock(ChangeRequestService.class);
    private final GitLabWebhookFanoutService fanout = mock(GitLabWebhookFanoutService.class);
    private final AerospikeWebhookDeliveryStore deliveries = mock(AerospikeWebhookDeliveryStore.class);
    private final AerospikeCommentStore comments = mock(AerospikeCommentStore.class);
    private final GitLabClient client = mock(GitLabClient.class);
    private final GitLabOAuthService oauth = mock(GitLabOAuthService.class);
    private final UserMergeRequestService associations = mock(UserMergeRequestService.class);
    private final Map<String, WebhookDelivery> deliveryRecords = new HashMap<>();
    private GitLabCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        when(deliveries.getWithVersion(anyString())).thenAnswer(call ->
                Optional.ofNullable(deliveryRecords.get(call.getArgument(0))).map(value -> Versioned.of(value, 1L)));
        doAnswer(call -> {
            final WebhookDelivery delivery = call.getArgument(0);
            deliveryRecords.put(delivery.id(), delivery);
            return null;
        }).when(deliveries).create(any(WebhookDelivery.class));
        doAnswer(call -> {
            final WebhookDelivery delivery = call.getArgument(0);
            deliveryRecords.put(delivery.id(), delivery);
            return null;
        }).when(deliveries).checkAndUpdate(any(WebhookDelivery.class), anyLong());
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(true);
        service = new GitLabCallbackServiceImpl(new GitLabSyncConfiguration("secret"), deliveries, sync,
                changes, fanout, null, comments, client, oauth, associations);
    }

    @Test
    void testProcess_shouldUseDetailedStatusAndProviderDates_whenWebhookReportsCoarseMergeability() throws Exception {
        for (final var status : List.of("not_approved", "ci_must_pass", "mergeable", "")) {
            process(status, """
                    {"object_kind":"merge_request", "project":{"id":17352,"path_with_namespace":"devx/clio"},
                     "object_attributes":{"id":906419,"iid":86,"title":"Feature",
                       "state":"opened", "merge_status":"can_be_merged", "detailed_merge_status":"%s",
                       "created_at":"2026-09-10 10:00:00 UTC", "updated_at":"2026-09-16 11:32:44.123456 UTC"}}
                    """.formatted(status));
        }
        final var event = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, times(4)).synchronize(event.capture());
        assertEquals(List.of("not_approved", "ci_must_pass", "mergeable", "can_be_merged"),
                event.getAllValues().stream().map(value -> value.changeRequest().mergeStatus()).toList());
        final var mr = event.getValue().changeRequest();
        assertEquals(Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(), mr.createdAt());
        assertEquals(Instant.parse("2026-09-16T11:32:44.123456Z").toEpochMilli(), mr.providerUpdatedAt());
    }

    @Test
    void testProcess_shouldPreserveMissingFieldsAndAuthor_whenAnotherUserUpdatesDraft() throws Exception {
        final var previous = record("opened", "not_approved");
        when(changes.record(previous.id())).thenReturn(Optional.of(previous));
        process("draft", mr("\"draft\":true"));
        final var current = capturedMr();
        assertTrue(current.draft());
        assertEquals("Author", current.authorName());
        assertEquals(previous.reviewerIds(), current.reviewerIds());
        assertEquals(previous.assigneeIds(), current.assigneeIds());
        assertEquals(previous.labels(), current.labels());
        assertEquals(previous.description(), current.description());
        assertEquals(previous.createdAt(), current.createdAt());
        assertEquals(previous.blockingDiscussionsResolved(), current.blockingDiscussionsResolved());
        assertEquals(previous.hasConflicts(), current.hasConflicts());
    }

    @Test
    void testProcess_shouldClearExplicitLists_whenReviewersAndLabelsAreRemoved() throws Exception {
        when(changes.record("gitlab:10:12")).thenReturn(Optional.of(record("opened", "not_approved")));
        process("clear", mr("\"draft\":false").replace("\"project\":", "\"reviewers\":[],\"labels\":[],\"project\":"));
        final var current = capturedMr();
        assertTrue(current.reviewerIds().isEmpty());
        assertTrue(current.labels().isEmpty());
        assertFalse(current.draft());
    }

    @Test
    void testProcess_shouldKeepCompletionDates_whenCompletedMrIsEdited() throws Exception {
        for (final var state : List.of("merged", "closed")) {
            final var previous = record(state, "not_open");
            when(changes.record(previous.id())).thenReturn(Optional.of(previous));
            process(state, mr("\"state\":\"" + state + "\""));
        }
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync, times(2)).synchronize(captor.capture());
        assertEquals(30, captor.getAllValues().get(0).changeRequest().mergedAt());
        assertEquals(40, captor.getAllValues().get(1).changeRequest().closedAt());
    }

    @Test
    void testProcess_shouldSkipFanout_whenSnapshotIsRejected() throws Exception {
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(false);
        process("stale", mr("\"state\":\"closed\""));
        verifyNoInteractions(fanout);
    }

    @Test
    void testProcess_shouldRetryExpiredAndFailedDeliveries_whenEarlierAttemptDidNotFinish() throws Exception {
        for (final var status : List.of("processing", "failed")) {
            deliveryRecords.put(status, new WebhookDelivery(status, "Merge Request Hook", status, 1, 0));
            process(status, mr("\"draft\":false"));
            assertEquals("processed", deliveryRecords.get(status).status());
        }
        verify(sync, times(2)).synchronize(any(ChangeRequestSync.class));
    }

    @Test
    void testProcess_shouldSkipCompletedButRejectActiveDeliveries_whenDuplicateArrives() throws Exception {
        deliveryRecords.put("done", new WebhookDelivery("done", "hook", "processed", 1, 2));
        process("done", mr("\"draft\":false"));
        deliveryRecords.put("busy", new WebhookDelivery("busy", "hook", "processing", System.currentTimeMillis(), 0));
        assertThrows(ArnoldError.class, () -> process("busy", mr("\"draft\":false")));
        verifyNoInteractions(sync);
    }

    @Test
    void testProcess_shouldAllowRetry_whenProcessingFails() throws Exception {
        when(sync.synchronize(any(ChangeRequestSync.class))).thenThrow(new IllegalStateException("failure")).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> process("retry", mr("\"draft\":false")));
        assertEquals("failed", deliveryRecords.get("retry").status());
        process("retry", mr("\"draft\":false"));
        assertEquals("processed", deliveryRecords.get("retry").status());
    }

    @Test
    void testProcess_shouldPreserveCommentMetadata_whenSomeoneEditsExistingNote() throws Exception {
        final var position = new DiscussionPosition("base", "start", "head", "a.java", "a.java", 1L, 1L, null);
        final var suggestions = List.of(new SuggestionResponse(1, true, false, "old", "new", 1, 1));
        final var previous = new CommentRecord("gitlab:10:12:7", VersionControlProviderType.GITLAB, "gitlab:10:12",
                "discussion", 7, 1L, "author", "old", "published", false, true, false, false,
                "gitlab", 10, 20, 30, position, suggestions);
        when(comments.get(previous.id())).thenReturn(Optional.of(previous));
        process("note", """
                {"object_kind":"note","project":{"id":10},"merge_request":{"iid":12},
                 "user":{"id":2,"username":"editor"},"object_attributes":{"id":7,"author_id":1,
                 "noteable_type":"MergeRequest","note":"new","updated_at":"2026-09-16 10:00:00 UTC"}}
                """);
        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(sync).synchronize(captor.capture());
        final var current = captor.getValue();
        assertEquals(1L, current.authorId());
        assertEquals("author", current.authorUsername());
        assertEquals(10, current.createdAt());
        assertFalse(current.resolvable());
        assertTrue(current.resolved());
        assertEquals(position, current.position());
        assertEquals(suggestions, current.suggestions());
        assertEquals("new", current.body());
    }

    @Test
    void testProcess_shouldIgnoreNonMrNotes_whenMergeRequestIsAbsent() throws Exception {
        for (final var type : List.of("Issue", "Commit", "Snippet")) {
            process(type, "{\"object_kind\":\"note\",\"object_attributes\":{\"noteable_type\":\"" + type + "\"}}");
        }
        verifyNoInteractions(sync);
    }

    @Test
    void testProcess_shouldNotifyExistingReviewer_whenReviewIsRequestedAgain() throws Exception {
        final var previous = record("opened", "not_approved");
        when(changes.record(previous.id())).thenReturn(Optional.of(previous));
        process("review", mr("\"state\":\"opened\"").replace("\"project\":", """
                "changes":{"reviewers":[[{"id":2,"state":"approved","re_requested":false}],
                  [{"id":2,"state":"unreviewed","re_requested":true}]]},"project":
                """));
        final var current = capturedMr();
        assertEquals(List.of("unreviewed"), current.reviewerStates());
        verify(fanout).reviewRequested(current, Set.of(2L));
    }

    @Test
    void testProcess_shouldFetchGitLabReadiness_whenPipelineFinishes() throws Exception {
        final var previous = record("opened", "ci_still_running");
        final var current = record("opened", "not_approved");
        when(changes.record(previous.id())).thenReturn(Optional.of(previous));
        when(associations.associationsForMergeRequest(previous.id())).thenReturn(List.of(new UserChangeRequest(
                "association", "user", previous.id(), Set.of(MergeRequestRole.AUTHOR), true, true, 1, 2, 20)));
        when(oauth.validCredential("user")).thenReturn(new VersionControlCredential("credential", "user",
                VersionControlProviderType.GITLAB, "OAUTH", "token", null, "1", "author", Set.of(), null, "ACTIVE", 0, 0));
        when(client.getMergeRequest(10, 12, "token")).thenReturn(current);
        process("pipeline", """
                {"object_kind":"pipeline","project":{"id":99},"merge_request":{"iid":12,"target_project_id":10},
                  "object_attributes":{"id":50,"status":"success"}}
                """);
        assertEquals("not_approved", capturedMr().mergeStatus());
        verify(fanout).synchronize(previous, current);
    }

    @Test
    void testProcess_shouldKeepNewOwner_whenDeliveryLeaseIsReclaimed() throws Exception {
        when(sync.synchronize(any(ChangeRequestSync.class))).thenAnswer(call -> {
            final var newer = new WebhookDelivery("lease", "hook", "processing", System.currentTimeMillis() + 300_000, 0);
            deliveryRecords.put("lease", newer);
            return true;
        });
        assertThrows(ArnoldError.class, () -> process("lease", mr("\"draft\":false")));
        assertEquals("processing", deliveryRecords.get("lease").status());
    }

    @Test
    void testProcess_shouldUseNoteAuthorAndCreationDate_whenNewCommentIsDelivered() throws Exception {
        process("new-note", """
                {"object_kind":"note","project":{"id":10},"merge_request":{"iid":12},
                 "user":{"id":2,"username":"editor"},"object_attributes":{"id":7,"author_id":1,
                 "noteable_type":"MergeRequest","note":"new","resolvable":false,"resolved":false,
                 "created_at":"2026-09-10 10:00:00 UTC","updated_at":"2026-09-16 10:00:00 UTC"}}
                """);
        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(sync).synchronize(captor.capture());
        assertEquals(1L, captor.getValue().authorId());
        assertNull(captor.getValue().authorUsername());
        assertEquals(Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(), captor.getValue().createdAt());
        assertFalse(captor.getValue().resolvable());
    }

    private ChangeRequestRecord capturedMr() {
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync).synchronize(captor.capture());
        return captor.getValue().changeRequest();
    }

    private void process(final String delivery, final String json) throws Exception {
        service.process("secret", delivery.isEmpty() ? "empty-status" : delivery, "hook",
                mapper.readValue(json, GitLabWebhookRequest.class));
    }

    private String mr(final String attributes) {
        return """
                {"object_kind":"merge_request","project":{"id":10},"user":{"id":2,"username":"reviewer"},
                  "object_attributes":{"id":100,"iid":12,"author_id":1,"updated_at":"2026-09-16 10:00:00 UTC",%s}}
                """.formatted(attributes);
    }

    private ChangeRequestRecord record(final String state, final String status) {
        return new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100", 12,
                "devx/test", "Feature", "Description", "https://gitlab.test/devx/test/-/merge_requests/12",
                "feature", "main", "sha", state, status, false, 1L, "Author", List.of(3L), List.of("Assignee"),
                List.of(2L), List.of("Reviewer"), List.of("approved"), List.of("backend"), 10, 30, 40,
                false, true, 20, 30, false);
    }
}
