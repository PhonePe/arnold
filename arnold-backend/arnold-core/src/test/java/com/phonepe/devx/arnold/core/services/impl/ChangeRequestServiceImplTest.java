package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.Versioned;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.review.ReviewProgress;
import com.phonepe.devx.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerStatusResponse;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffChange;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffGroup;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResultRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffStep;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChangeRequestServiceImplTest {
    private final AerospikeChangeRequestStore changeRequestStore = mock(AerospikeChangeRequestStore.class);
    private final VersionControlProviders providers = mock(VersionControlProviders.class);
    private final VersionControlProvider gitLabProvider = mock(VersionControlProvider.class);
    private final AerospikeReviewProgressStore reviewProgressStore = mock(AerospikeReviewProgressStore.class);
    private final AerospikeSmartDiffStore smartDiffStore = mock(AerospikeSmartDiffStore.class);
    private final ChangeRequestServiceImpl service = new ChangeRequestServiceImpl(changeRequestStore, providers,
            reviewProgressStore, smartDiffStore);

    @Test
    void testSynchronize_shouldRepairWebhookReceiptDatesButRejectStaleUpdates() {
        final var previous = mock(ChangeRequestRecord.class);
        final var incoming = mock(ChangeRequestRecord.class);
        when(incoming.id()).thenReturn("gitlab:17352:86");
        when(previous.createdAt()).thenReturn(300L);
        when(previous.providerUpdatedAt()).thenReturn(300L);
        when(previous.lastSyncedAt()).thenReturn(300L);
        when(incoming.createdAt()).thenReturn(100L);
        when(incoming.providerUpdatedAt()).thenReturn(200L);
        when(incoming.lastSyncedAt()).thenReturn(400L);
        when(changeRequestStore.getWithVersion(incoming.id())).thenReturn(Optional.of(Versioned.of(previous, 1L)));

        assertEquals(true, service.synchronize(incoming));
        verify(changeRequestStore).checkAndUpdate(incoming, 1L);

        when(previous.createdAt()).thenReturn(100L);
        assertEquals(false, service.synchronize(incoming));
        verify(changeRequestStore).checkAndUpdate(incoming, 1L);
    }

    @Test
    void testResolve_shouldSynchronizeResolvedMergeRequest_whenUrlIsValid() {
        final var url = "https://gitlab.example/team/repo/-/merge_requests/12";
        final var mergeRequest = mergeRequest();
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
        when(gitLabProvider.resolveChangeRequest(url, "user-1")).thenReturn(mergeRequest);
        when(changeRequestStore.getWithVersion("gitlab:10:12")).thenReturn(Optional.empty());

        final var result = service.resolve(VersionControlProviderType.GITLAB, url, "user-1");

        assertEquals(mergeRequest, result);
        verify(providers).get(VersionControlProviderType.GITLAB);
        verify(gitLabProvider).resolveChangeRequest(url, "user-1");
        verify(changeRequestStore).getWithVersion("gitlab:10:12");
        verify(changeRequestStore).create(argThat(record ->
                record.id().equals("gitlab:10:12") && record.providerId().equals("100")));
    }

    @Test
    void testGetDiff_shouldGetFromGitLabProvider_whenIdentifiersAreValid() {
        final var diff = "diff --git a/example.txt b/example.txt";
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
        when(gitLabProvider.getChangeRequestDiff(10L, 12L, "user-1")).thenReturn(diff);

        final var result = service.getDiff(10L, 12L, "user-1");

        assertEquals(diff, result);
        verify(providers).get(VersionControlProviderType.GITLAB);
        verify(gitLabProvider).getChangeRequestDiff(10L, 12L, "user-1");
        verifyNoInteractions(changeRequestStore);
    }

    @Test
    void testGetViewedFiles_shouldReturnEmpty_whenHeadChanged() {
        when(reviewProgressStore.get("progress:user-1:gitlab:10:12:new-head")).thenReturn(Optional.empty());

        final var result = service.getViewedFiles(10L, 12L, "new-head", "user-1");

        assertEquals(List.of(), result.filePaths());
        assertEquals(List.of(), result.storyStepIds());
        assertEquals(Map.of(), result.focusAreaFeedback());
        assertEquals("new-head", result.headSha());
    }

    @Test
    void testUpdateViewedFiles_shouldCreateProgress_whenFirstFileIsViewed() {
        final var request = ViewedFilesRequest.builder().headSha("a".repeat(40))
                .filePaths(List.of("src/A.java")).storyStepIds(List.of("step-1"))
                .focusAreaFeedback(Map.of("focus-1", "useful")).build();
        when(reviewProgressStore.get("progress:user-1:gitlab:10:12:" + "a".repeat(40)))
                .thenReturn(Optional.empty());

        final var result = service.updateViewedFiles(10L, 12L, request, "user-1");

        assertEquals(List.of("src/A.java"), result.filePaths());
        assertEquals(List.of("step-1"), result.storyStepIds());
        assertEquals(Map.of("focus-1", "useful"), result.focusAreaFeedback());
        verify(reviewProgressStore).create(argThat(progress -> progress.filePaths().equals(List.of("src/A.java"))
                && progress.storyStepIds().equals(List.of("step-1"))
                && progress.focusAreaFeedback().equals(Map.of("focus-1", "useful"))));
    }

    @Test
    void testUpdateViewedFiles_shouldKeepStoryProgress_whenStepIdsAreOmitted() {
        final var headSha = "a".repeat(40);
        final var id = "progress:user-1:gitlab:10:12:" + headSha;
        final var request = ViewedFilesRequest.builder().headSha(headSha).filePaths(List.of("src/B.java")).build();
        when(reviewProgressStore.get(id)).thenReturn(Optional.of(
                new ReviewProgress(id, headSha, List.of("src/A.java"), List.of("step-1"),
                        Map.of("focus-1", "not-useful"), 1)));

        final var result = service.updateViewedFiles(10L, 12L, request, "user-1");

        assertEquals(List.of("src/B.java"), result.filePaths());
        assertEquals(List.of("step-1"), result.storyStepIds());
        assertEquals(Map.of("focus-1", "not-useful"), result.focusAreaFeedback());
        verify(reviewProgressStore).update(argThat(progress -> progress.storyStepIds().equals(List.of("step-1"))
                && progress.focusAreaFeedback().equals(Map.of("focus-1", "not-useful"))));
    }

    @Test
    void testGet_shouldRefreshMergeRequest_whenStoredUserNamesAreMissing() {
        final var stored = new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100",
                12L, "team/repo", "Title", null, "https://gitlab.example/team/repo/-/merge_requests/12",
                "feature", "main", "abc", "opened", "can_be_merged", false, 1L, null, List.of(2L), List.of(),
                List.of(3L), List.of(), List.of(), List.of("backend"), 0, 0, 0, false, true, 1000L, 1000L, true);
        final var refreshed = mergeRequest();
        when(changeRequestStore.get("gitlab:10:12")).thenReturn(Optional.of(stored));
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
        when(gitLabProvider.resolveChangeRequest(stored.webUrl(), "user-1")).thenReturn(refreshed);
        when(changeRequestStore.getWithVersion("gitlab:10:12")).thenReturn(Optional.of(Versioned.of(stored, 1L)));

        final var result = service.get(VersionControlProviderType.GITLAB, "10", 12L, "user-1");

        assertEquals("Alice", result.authorName());
        verify(changeRequestStore).checkAndUpdate(argThat(record -> "Alice".equals(record.authorName()) && record.projectArchived()),
                argThat(version -> version.equals(1L)));
    }

    @Test
    void testGetReview_shouldUseWebhookReviewerState_whenAvailable() {
        final var stored = new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100",
                12L, "team/repo", "Title", null, "https://gitlab.example/team/repo/-/merge_requests/12",
                "feature", "main", "abc", "opened", "requested_changes", false, 1L, "Alice", List.of(),
                List.of(), List.of(3L), List.of("Carol"), List.of("requested_changes"), List.of(),
                0, 0, 0, false, true, 1000L, 1000L, false);
        final var live = new MergeRequestReviewResponse("requested_changes", false, true, "success", "pipeline", 1,
                1, false, List.of(new ReviewerStatusResponse(3L, "Carol", "carol", null, "awaiting_review")));
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
        when(gitLabProvider.getChangeRequestReview(10L, 12L, "user-1")).thenReturn(live);
        when(changeRequestStore.get("gitlab:10:12")).thenReturn(Optional.of(stored));

        final var result = service.getReview(10L, 12L, "user-1");

        assertEquals("requested_changes", result.reviewers().get(0).status());
    }

    @Nested
    class SmartDiff {
        private final String headSha = "a".repeat(40);
        private final String id = "smart-diff:gitlab:10:12:" + headSha;
        private final String executionId = "123e4567-e89b-42d3-a456-426614174000";

        @BeforeEach
        void setUp() {
            final var stored = mock(ChangeRequestRecord.class);
            when(stored.webUrl()).thenReturn("https://gitlab.example/team/repo/-/merge_requests/12");
            when(stored.headSha()).thenReturn(headSha);
            when(changeRequestStore.get("gitlab:10:12")).thenReturn(Optional.of(stored));
            when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
            when(gitLabProvider.resolveChangeRequest(stored.webUrl(), "user-1")).thenReturn(smartMergeRequest());
        }

        @Test
        void testGetSmartDiff_shouldReturnNotRequested_whenRecordIsMissing() {
            when(smartDiffStore.get(id)).thenReturn(Optional.empty());

            final var result = service.getSmartDiff(10L, 12L, headSha);

            assertEquals("not_requested", result.status());
            assertEquals(List.of(), result.groups());
            assertEquals(0, result.updatedAt());
            assertEquals(null, result.errorMessage());
        }

        @Test
        void testRequestSmartDiff_shouldCreateQueuedRecord_whenRecordIsMissing() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.empty());

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1");

            assertEquals("queued", result.status());
            verify(smartDiffStore).create(argThat(record -> id.equals(record.id()) && record.updatedAt() > 0));
            verify(gitLabProvider).triggerSmartDiff(eq(smartMergeRequest().webUrl()), eq(10L), eq(12L), eq(headSha),
                    anyString());
        }

        @Test
        void testRequestSmartDiff_shouldRejectRequest_whenHeadShaIsStale() {
            when(gitLabProvider.resolveChangeRequest(anyString(), org.mockito.ArgumentMatchers.eq("user-1")))
                    .thenReturn(mergeRequest());

            final var error = assertThrows(ArnoldError.class, () -> service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1"));

            assertEquals(ArnoldErrorCode.CONFLICT_ERROR, error.getErrorCode());
            verifyNoInteractions(smartDiffStore);
        }

        @Test
        void testRequestSmartDiff_shouldReplaceExecution_whenQueuedRecordIsStale() {
            final var stale = new SmartDiffRecord(id, headSha, executionId, "queued", null, List.of(),
                    System.currentTimeMillis() - 136 * 60 * 1000L, null);
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(stale, 8L)));
            final var replacement = new AtomicReference<SmartDiffRecord>();
            doAnswer(invocation -> { replacement.set(invocation.getArgument(0)); return null; })
                    .when(smartDiffStore).checkAndUpdate(any(SmartDiffRecord.class), any());

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1");

            assertEquals("queued", result.status());
            assertNotEquals(executionId, replacement.get().executionId());
            verify(gitLabProvider).triggerSmartDiff(smartMergeRequest().webUrl(), 10L, 12L, headSha,
                    replacement.get().executionId());
        }

        @Test
        void testRequestSmartDiff_shouldMarkRecordFailed_whenDispatchFails() {
            final var storedSmartDiff = new AtomicReference<SmartDiffRecord>();
            when(smartDiffStore.getWithVersion(id)).thenAnswer(invocation -> Optional.ofNullable(storedSmartDiff.get())
                    .map(record -> Versioned.of(record, 9L)));
            doAnswer(invocation -> { storedSmartDiff.set(invocation.getArgument(0)); return null; })
                    .when(smartDiffStore).create(any(SmartDiffRecord.class));
            doAnswer(invocation -> { storedSmartDiff.set(invocation.getArgument(0)); return null; })
                    .when(smartDiffStore).checkAndUpdate(any(SmartDiffRecord.class), any());
            doThrow(new RuntimeException("timeout")).when(gitLabProvider)
                    .triggerSmartDiff(anyString(), eq(10L), eq(12L), eq(headSha), anyString());

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1");

            assertEquals("failed", result.status());
            assertEquals("Could not start Smart Diff generation. Please retry.", result.errorMessage());
            assertEquals("failed", storedSmartDiff.get().status());
        }

        @Test
        void testRequestSmartDiff_shouldReturnStoredRecord_whenRequestIsAlreadyCompleted() {
            final var completed = record("completed", groups(), null);
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(completed, 2L)));

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1");

            assertEquals("completed", result.status());
            assertEquals(groups(), result.groups());
        }

        @Test
        void testRequestSmartDiff_shouldReplaceCompletedExecution_whenRegenerationIsRequested() {
            final var completed = record("completed", groups(), null);
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(completed, 2L)));
            final var replacement = new AtomicReference<SmartDiffRecord>();
            doAnswer(invocation -> { replacement.set(invocation.getArgument(0)); return null; })
                    .when(smartDiffStore).checkAndUpdate(any(SmartDiffRecord.class), any());

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).regenerate(true).build(), "user-1");

            assertEquals("queued", result.status());
            assertNotEquals(executionId, replacement.get().executionId());
            verify(gitLabProvider).triggerSmartDiff(smartMergeRequest().webUrl(), 10L, 12L, headSha,
                    replacement.get().executionId());
        }

        @Test
        void testRequestSmartDiff_shouldKeepActiveExecution_whenRegenerationIsRequested() {
            final var running = new SmartDiffRecord(id, headSha, executionId, "running", null, List.of(),
                    System.currentTimeMillis(), null);
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(running, 2L)));

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).regenerate(true).build(), "user-1");

            assertEquals("running", result.status());
            verify(gitLabProvider, never()).triggerSmartDiff(anyString(), eq(10L), eq(12L), eq(headSha), anyString());
        }

        @Test
        void testRequestSmartDiff_shouldResetFailedRecord_whenRetried() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(
                    record("failed", List.of(), "producer failed"), 3L)));

            final var result = service.requestSmartDiff(10L, 12L,
                    SmartDiffRequest.builder().headSha(headSha).build(), "user-1");

            assertEquals("queued", result.status());
            assertEquals(null, result.errorMessage());
            verify(smartDiffStore).checkAndUpdate(argThat(record -> "queued".equals(record.status())),
                    argThat(version -> version.equals(3L)));
        }

        @Test
        void testUpdateSmartDiff_shouldCompleteQueuedRecord_whenProducerSucceeds() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(
                    record("queued", List.of(), null), 4L)));
            final var request = resultRequest("completed", groups(), null);

            final var result = service.updateSmartDiff(10L, 12L, request);

            assertEquals("completed", result.status());
            assertEquals(groups(), result.groups());
            verify(smartDiffStore).checkAndUpdate(argThat(record -> "completed".equals(record.status())),
                    argThat(version -> version.equals(4L)));
        }

        @Test
        void testUpdateSmartDiff_shouldReturnStoredResult_whenTerminalCallbackIsDuplicated() {
            final var completed = record("completed", groups(), null);
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(completed, 5L)));

            final var result = service.updateSmartDiff(10L, 12L, resultRequest("completed", List.of(), null));

            assertEquals(groups(), result.groups());
        }

        @Test
        void testUpdateSmartDiff_shouldRejectCallback_whenQueuedRecordIsMissing() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.empty());

            final var error = assertThrows(ArnoldError.class, () -> service.updateSmartDiff(10L, 12L,
                    resultRequest("running", List.of(), null)));

            assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR, error.getErrorCode());
        }

        @Test
        void testUpdateSmartDiff_shouldRejectCallback_whenExecutionWasReplaced() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(
                    record("queued", List.of(), null), 4L)));
            final var obsolete = SmartDiffResultRequest.builder()
                    .executionId("223e4567-e89b-42d3-a456-426614174000").headSha(headSha)
                    .status("running").groups(List.of()).build();

            final var error = assertThrows(ArnoldError.class,
                    () -> service.updateSmartDiff(10L, 12L, obsolete));

            assertEquals(ArnoldErrorCode.CONFLICT_ERROR, error.getErrorCode());
        }

        @Test
        void testUpdateSmartDiff_shouldRejectTransition_whenTerminalStatusConflicts() {
            when(smartDiffStore.getWithVersion(id)).thenReturn(Optional.of(Versioned.of(
                    record("completed", groups(), null), 5L)));

            final var error = assertThrows(ArnoldError.class, () -> service.updateSmartDiff(10L, 12L,
                    resultRequest("failed", List.of(), "late failure")));

            assertEquals(ArnoldErrorCode.CONFLICT_ERROR, error.getErrorCode());
        }

        @Test
        void testUpdateSmartDiff_shouldRetry_whenAerospikeGenerationChanges() {
            final var queued = record("queued", List.of(), null);
            when(smartDiffStore.getWithVersion(id))
                    .thenReturn(Optional.of(Versioned.of(queued, 6L)), Optional.of(Versioned.of(queued, 7L)));
            doThrow(new ConcurrentUpdateException("race")).doNothing()
                    .when(smartDiffStore).checkAndUpdate(any(SmartDiffRecord.class), any());

            final var result = service.updateSmartDiff(10L, 12L,
                    resultRequest("completed", groups(), null));

            assertEquals("completed", result.status());
            verify(smartDiffStore).checkAndUpdate(any(SmartDiffRecord.class), argThat(version -> version.equals(7L)));
        }

        @Test
        void testGetSmartDiff_shouldShareRecord_whenCalledByMultipleReviewers() {
            final var completed = record("completed", groups(), null);
            when(smartDiffStore.get(id)).thenReturn(Optional.of(completed));

            assertEquals(service.getSmartDiff(10L, 12L, headSha), service.getSmartDiff(10L, 12L, headSha));
        }

        private SmartDiffRecord record(final String status, final List<SmartDiffGroup> groups,
                                       final String errorMessage) {
            return new SmartDiffRecord(id, headSha, executionId, status,
                    "completed".equals(status) ? "Review the API first." : null, groups, 1234L, errorMessage);
        }

        private SmartDiffResultRequest resultRequest(final String status, final List<SmartDiffGroup> groups,
                                                     final String errorMessage) {
            return SmartDiffResultRequest.builder().executionId(executionId).headSha(headSha).status(status)
                    .summary("completed".equals(status) ? "Review the API first." : null).groups(groups)
                    .errorMessage(errorMessage).build();
        }
    }

    private static List<SmartDiffGroup> groups() {
        final var change = SmartDiffChange.builder().filePath("src/A.java").oldStartLine(10).oldEndLine(14)
                .newStartLine(12).newEndLine(18).build();
        final var step = SmartDiffStep.builder().id("step-1").title("API").explanation("Review the API")
                .changes(List.of(change)).build();
        return List.of(SmartDiffGroup.builder().id("group-1").title("Contract").rationale("Start here")
                .steps(List.of(step)).build());
    }

    private static MergeRequestResponse mergeRequest() {
        return new MergeRequestResponse("gitlab:10:12", "GITLAB", "10", "100", 12L, "team/repo", "Title", null,
                "https://gitlab.example/team/repo/-/merge_requests/12", "feature", "main", "abc", "opened",
                "can_be_merged", false, 1L, "Alice", List.of(2L), List.of("Bob"), List.of(3L),
                List.of("Carol"), List.of("backend"), 0, 0, 0, false, true, 1000L, 1100L);
    }

    private static MergeRequestResponse smartMergeRequest() {
        final var mergeRequest = mergeRequest();
        return new MergeRequestResponse(mergeRequest.id(), mergeRequest.provider(), mergeRequest.repositoryId(),
                mergeRequest.providerId(), mergeRequest.number(), mergeRequest.repositoryPath(), mergeRequest.title(),
                mergeRequest.description(), mergeRequest.webUrl(), mergeRequest.sourceBranch(),
                mergeRequest.targetBranch(), "a".repeat(40), mergeRequest.state(), mergeRequest.mergeStatus(),
                mergeRequest.draft(), mergeRequest.authorId(), mergeRequest.authorName(), mergeRequest.assigneeIds(),
                mergeRequest.assigneeNames(), mergeRequest.reviewerIds(), mergeRequest.reviewerNames(),
                mergeRequest.labels(), mergeRequest.createdAt(), mergeRequest.mergedAt(), mergeRequest.closedAt(),
                mergeRequest.hasConflicts(), mergeRequest.blockingDiscussionsResolved(), mergeRequest.providerUpdatedAt(),
                mergeRequest.lastSyncedAt());
    }
}
