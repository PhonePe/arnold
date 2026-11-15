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

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.core.provider.VersionControlProviders;
import com.phonepe.arnold.core.services.AnalysisExecutionService;
import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerStatusResponse;
import com.phonepe.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.arnold.models.mergerequest.SmartDiffResultRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChangeRequestServiceImplCoverageTest {
    private static final String HEAD = "a".repeat(40);
    private static final String SMART_ID = "smart-diff:gitlab:10:12:" + HEAD;
    private static final String EXECUTION = "123e4567-e89b-42d3-a456-426614174000";

    private final AerospikeChangeRequestStore changeStore = mock(AerospikeChangeRequestStore.class);
    private final VersionControlProviders providers = mock(VersionControlProviders.class);
    private final VersionControlProvider provider = mock(VersionControlProvider.class);
    private final AerospikeReviewProgressStore progressStore = mock(AerospikeReviewProgressStore.class);
    private final AerospikeSmartDiffStore smartStore = mock(AerospikeSmartDiffStore.class);
    private final RunnerDispatcher pipelines = mock(RunnerDispatcher.class);
    private final ArnoldSignalPublisher signals = mock(ArnoldSignalPublisher.class);
    private final ProjectionQueue projections = mock(ProjectionQueue.class);
    private ChangeRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(provider);
        service = new ChangeRequestServiceImpl(changeStore, providers, progressStore, smartStore, pipelines, signals,
                mock(AnalysisExecutionService.class), projections);
    }

    private static MergeRequestResponse mr(final String head) {
        return new MergeRequestResponse("gitlab:10:12", "GITLAB", "10", "100", 12L, "team/repo", "Title", null,
                "https://gitlab/mr/12", "feature", "main", head, "opened", "can_be_merged", false, 1L, "Alice",
                List.of(2L), List.of("Bob"), List.of(3L), List.of("Carol"), List.of("backend"), 0, 0, 0, false, true,
                1000L, 1100L);
    }

    private static ChangeRequestRecord record(final long updatedAt) {
        return new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100", 12, "team/repo",
                "Title", null, "https://gitlab/mr/12", "feature", "main", HEAD, "opened", "x", false, 1L, "Alice",
                List.of(), List.of(), List.of(3L, 4L), List.of("C", "D"), List.of("approved", "weird"), List.of(), 0, 0, 0,
                false, true, updatedAt, updatedAt, false);
    }

    private static SmartDiffRecord smart(final String status, final String requestedBy) {
        return new SmartDiffRecord(SMART_ID, HEAD, EXECUTION, status, null, List.of(), 1234L, "boom", status, 0,
                requestedBy, null);
    }

    private static SmartDiffResultRequest result(final String status) {
        return SmartDiffResultRequest.builder().executionId(EXECUTION).headSha(HEAD).status(status).groups(List.of())
                .errorMessage("boom").build();
    }

    @Test
    void testDelegatingCalls_shouldForwardToGitLabProvider() {
        final var candidate = new ReviewerCandidateResponse(1, "n", "u", null);
        final var versions = List.of(new MergeRequestDiffVersionResponse(1, "b", "s", "h", 1, "t"));
        final var files = mock(MergeRequestFilesResponse.class);
        when(provider.addChangeRequestReviewer(1, 2, "bob", "u")).thenReturn(mr(HEAD));
        when(provider.removeChangeRequestReviewer(1, 2, 3, "u")).thenReturn(mr(HEAD));
        when(provider.searchChangeRequestReviewers(1, "q", "u")).thenReturn(List.of(candidate));
        when(provider.getChangeRequestDiffVersion(1, 2, 3, "u")).thenReturn("diff");
        when(provider.listChangeRequestDiffVersions(1, 2, "u")).thenReturn(versions);
        when(provider.getChangeRequestFiles(1, 2, "o", "n", "b", "h", "u")).thenReturn(files);

        assertEquals(mr(HEAD), service.addReviewer(VersionControlProviderType.GITLAB, 1, 2, "bob", "u"));
        assertEquals(mr(HEAD), service.removeReviewer(VersionControlProviderType.GITLAB, 1, 2, 3, "u"));
        assertEquals(List.of(candidate), service.searchReviewers(VersionControlProviderType.GITLAB, 1, "q", "u"));
        assertEquals("diff", service.getDiffVersion(VersionControlProviderType.GITLAB, 1, 2, 3, "u"));
        assertEquals(versions, service.listDiffVersions(VersionControlProviderType.GITLAB, 1, 2, "u"));
        assertSame(files, service.getFiles(VersionControlProviderType.GITLAB, 1, 2, "o", "n", "b", "h", "u"));
    }

    @Test
    void testRecords_shouldShortCircuitOnEmptyAndLookupOtherwise() {
        when(changeStore.get("id")).thenReturn(Optional.of(record(1)));
        when(changeStore.get(List.of("id"))).thenReturn(Map.of("id", record(1)));

        assertTrue(service.record("id").isPresent());
        assertTrue(service.records(List.<String>of()).isEmpty());
        assertEquals(1, service.records((Collection<String>) List.of("id")).size());
        verify(changeStore, never()).get(List.of());
        assertEquals("GITLAB", service.response(record(1)).provider());
    }

    @Test
    void testGet_shouldThrowNotFound_andSkipRefreshWhenNamesPresent() {
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.empty());
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR,
                assertThrows(ArnoldError.class, () -> service.get(VersionControlProviderType.GITLAB, "10", 12, "u"))
                        .getErrorCode());
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.of(record(1)));
        assertEquals("Alice", service.get(VersionControlProviderType.GITLAB, "10", 12, "u").authorName());
        verify(provider, never()).resolveChangeRequest(anyString(), anyString());
    }

    @Test
    void testGetReview_shouldMapStoredReviewerStates() {
        final var live = new MergeRequestReviewResponse("s", false, true, "ok", "url", 1, 1, false, List.of(
                new ReviewerStatusResponse(3L, "C", "c", null, "approved"),
                new ReviewerStatusResponse(4L, "D", "d", null, "x"),
                new ReviewerStatusResponse(9L, "Z", "z", null, "pending")));
        when(provider.getChangeRequestReview(10, 12, "u")).thenReturn(live);
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.of(record(1)));

        final var statuses = service.getReview(VersionControlProviderType.GITLAB, 10, 12, "u").reviewers().stream()
                .map(ReviewerStatusResponse::status).toList();

        assertEquals(List.of("approved", "awaiting_review", "pending"), statuses);
    }

    @Test
    void testGetReview_shouldReturnLive_whenStoredMissingOrMisaligned() {
        final var live = new MergeRequestReviewResponse("s", false, true, "ok", "url", 1, 1, false, List.of());
        when(provider.getChangeRequestReview(10, 12, "u")).thenReturn(live);
        assertSame(live, service.getReview(VersionControlProviderType.GITLAB, 10, 12, "u"));
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.of(record(1).withReviewerStates(List.of())));
        assertSame(live, service.getReview(VersionControlProviderType.GITLAB, 10, 12, "u"));
    }

    @Test
    void testSynchronize_shouldRetryOnCreateRaceAndConcurrentUpdate_thenGiveUp() {
        final var incoming = record(5);
        when(changeStore.getWithVersion("gitlab:10:12")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(Versioned.of(record(1), 1L)));
        doThrow(new IllegalStateException("exists")).when(changeStore).create(incoming);
        doThrow(new ConcurrentUpdateException("c")).doNothing().when(changeStore).checkAndUpdate(any(), anyLong());
        assertTrue(service.synchronize(incoming));

        doThrow(new ConcurrentUpdateException("c")).when(changeStore).checkAndUpdate(any(), anyLong());
        assertEquals(ArnoldErrorCode.DB_ERROR,
                assertThrows(ArnoldError.class, () -> service.synchronize(incoming)).getErrorCode());
    }

    @Test
    void testUpdateViewedFiles_shouldDeduplicateAndProject() {
        final var request = com.phonepe.arnold.models.mergerequest.ViewedFilesRequest.builder().headSha(HEAD)
                .filePaths(List.of("a", "a")).storyStepIds(List.of("s", "s")).focusAreaFeedback(Map.of()).build();
        when(progressStore.get(anyString())).thenReturn(Optional.empty());
        final var response = service.updateViewedFiles(VersionControlProviderType.GITLAB, 10, 12, request, "u");
        assertEquals(List.of("a"), response.filePaths());
        assertEquals(List.of("s"), response.storyStepIds());
        verify(projections).enqueueBestEffort(eq(ProjectionQueue.REVIEW_PROGRESS), anyString(), anyLong());
    }

    private void stubLiveHead(final String head) {
        final var stored = mock(ChangeRequestRecord.class);
        when(stored.webUrl()).thenReturn("https://gitlab/mr/12");
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.of(stored));
        when(provider.resolveChangeRequest("https://gitlab/mr/12", "u")).thenReturn(mr(head));
    }

    @Test
    void testRequestSmartDiff_shouldRejectUnknownMergeRequestAndMismatchedHead() {
        final var request = SmartDiffRequest.builder().headSha(HEAD).build();
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.empty());
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR,
                assertThrows(ArnoldError.class, () -> service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, request, "u")).getErrorCode());
        stubLiveHead("b".repeat(40));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, request, "u")).getErrorCode());
    }

    @Test
    void testRequestSmartDiff_shouldGiveUp_whenCreateAndUpdateKeepRacing() {
        stubLiveHead(HEAD);
        when(changeStore.getWithVersion(anyString())).thenReturn(Optional.empty());
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("race")).when(smartStore).create(any());
        final var request = SmartDiffRequest.builder().headSha(HEAD).build();
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, request, "u")).getErrorCode());

        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("failed", "u"), 1L)));
        doThrow(new ConcurrentUpdateException("c")).when(smartStore).checkAndUpdate(any(), anyLong());
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, request, "u")).getErrorCode());
    }

    @Test
    void testRequestSmartDiff_shouldKeepDispatchedResponse_whenPipelineRecordUpdateFails() {
        stubLiveHead(HEAD);
        when(changeStore.getWithVersion(anyString())).thenReturn(Optional.empty());
        final var queuedRef = new java.util.concurrent.atomic.AtomicReference<SmartDiffRecord>();
        doAnswer(call -> {
            queuedRef.set(call.getArgument(0));
            return null;
        }).when(smartStore).create(any(SmartDiffRecord.class));
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.empty())
                .thenAnswer(call -> Optional.of(Versioned.of(queuedRef.get(), 1L)));
        when(pipelines.submit(anyMap())).thenReturn(9L);
        when(pipelines.jobUrl(9L)).thenReturn("https://pipeline/9");
        doThrow(new IllegalStateException("db")).when(smartStore).checkAndUpdate(any(), anyLong());

        final var response = service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, SmartDiffRequest.builder().headSha(HEAD).build(), "u");

        assertEquals("https://pipeline/9", response.pipelineUrl());
    }

    @Test
    void testRequestSmartDiff_shouldReturnCurrentRecord_whenDispatchFailsAfterExecutionReplaced() {
        stubLiveHead(HEAD);
        when(changeStore.getWithVersion(anyString())).thenReturn(Optional.empty());
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(Versioned.of(smart("running", "u"), 1L)));
        when(pipelines.submit(anyMap())).thenThrow(new IllegalStateException("down"));

        final var response = service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, SmartDiffRequest.builder().headSha(HEAD).build(), "u");

        assertEquals("running", response.status());
    }

    @Test
    void testRequestSmartDiff_shouldGiveUp_whenFailingDispatchKeepsRacing() {
        stubLiveHead(HEAD);
        when(changeStore.getWithVersion(anyString())).thenReturn(Optional.empty());
        final var queuedRef = new java.util.concurrent.atomic.AtomicReference<SmartDiffRecord>();
        doAnswer(call -> {
            queuedRef.set(call.getArgument(0));
            return null;
        }).when(smartStore).create(any(SmartDiffRecord.class));
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.empty())
                .thenAnswer(call -> Optional.of(Versioned.of(queuedRef.get(), 1L)));
        when(pipelines.submit(anyMap())).thenThrow(new IllegalStateException("down"));
        doThrow(new ConcurrentUpdateException("c")).when(smartStore).checkAndUpdate(any(), anyLong());

        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, assertThrows(ArnoldError.class,
                () -> service.requestSmartDiff(VersionControlProviderType.GITLAB, 10, 12, SmartDiffRequest.builder().headSha(HEAD).build(), "u"))
                .getErrorCode());
    }

    @Test
    void testUpdateSmartDiff_shouldRejectCompletion_whenHeadMovedOrMergeRequestMissing() {
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("running", "u"), 1L)));
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.empty());
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR,
                assertThrows(ArnoldError.class, () -> service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("completed"))).getErrorCode());
        final var moved = mock(ChangeRequestRecord.class);
        when(moved.headSha()).thenReturn("c".repeat(40));
        when(changeStore.get("gitlab:10:12")).thenReturn(Optional.of(moved));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("completed"))).getErrorCode());
    }

    @Test
    void testUpdateSmartDiff_shouldRejectBackwardsTransitions() {
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("failed", "u"), 1L)));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("running"))).getErrorCode());
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("completed", "u"), 1L)));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("failed"))).getErrorCode());
    }

    @Test
    void testUpdateSmartDiff_shouldNotifyRequester_whenFailed_andSkipWithoutRequester() {
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("queued", "u"), 1L)));
        service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("failed"));
        final var captor = ArgumentCaptor.forClass(NotificationRequested.class);
        verify(signals).publish(captor.capture());
        assertEquals("Story mode generation failed", captor.getValue().title());
        assertEquals("boom", captor.getValue().body());
        assertEquals("/review/GITLAB/10/12", captor.getValue().deepLink());

        reset(signals);
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("queued", null), 1L)));
        service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("failed"));
        verifyNoInteractions(signals);
    }

    @Test
    void testUpdateSmartDiff_shouldGiveUp_whenUpdatesKeepConflicting() {
        when(smartStore.getWithVersion(SMART_ID)).thenReturn(Optional.of(Versioned.of(smart("queued", "u"), 1L)));
        doThrow(new ConcurrentUpdateException("c")).when(smartStore).checkAndUpdate(any(), anyLong());
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                assertThrows(ArnoldError.class, () -> service.updateSmartDiff(VersionControlProviderType.GITLAB, 10, 12, result("failed"))).getErrorCode());
    }
}
