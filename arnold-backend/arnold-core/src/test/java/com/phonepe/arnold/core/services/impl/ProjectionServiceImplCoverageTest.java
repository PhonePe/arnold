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

package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.comment.CommentRecord;
import com.phonepe.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.arnold.core.model.review.AnalysisOutcome;
import com.phonepe.arnold.core.model.review.ArnoldReview;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.arnold.models.analysis.TokenUsage;
import com.phonepe.arnold.models.es.AnalysisRunDocument;
import com.phonepe.arnold.models.es.CommentDocument;
import com.phonepe.arnold.models.es.GroupAnalysisRunDocument;
import com.phonepe.arnold.models.es.MergeRequestDocument;
import com.phonepe.arnold.models.es.ReviewEngagementDocument;
import com.phonepe.arnold.models.es.SmartDiffDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionServiceImplCoverageTest {
    private static final String MR_ID = "gitlab:10:12";

    private final AerospikeProjectionEventStore events = mock(AerospikeProjectionEventStore.class);
    private final AerospikeChangeRequestStore mergeRequests = mock(AerospikeChangeRequestStore.class);
    private final AerospikeCommentStore comments = mock(AerospikeCommentStore.class);
    private final ReviewStateStore reviewStates = mock(ReviewStateStore.class);
    private final AerospikeAnalysisRunStore runs = mock(AerospikeAnalysisRunStore.class);
    private final AerospikeSmartDiffStore smartDiffs = mock(AerospikeSmartDiffStore.class);
    private final AerospikeGroupAnalysisRunStore groupRuns = mock(AerospikeGroupAnalysisRunStore.class);
    private final ESStorageManager es = mock(ESStorageManager.class);
    private final ProjectionServiceImpl service = new ProjectionServiceImpl(events, mergeRequests, comments,
            reviewStates, runs, smartDiffs, groupRuns,
            mock(AerospikeReviewProgressStore.class),
            mock(AerospikeFlowJobUsageStore.class), es);

    private void givenEvent(final String type, final String aggregateId, final int attempts) {
        when(events.getByRefId("status:pending")).thenReturn(List.of(
                new ProjectionEvent("event-1", type, aggregateId, "upsert", "pending", attempts, 1, 0)));
    }

    private ChangeRequestRecord mergeRequest(final long createdAt, final long mergedAt) {
        final var mr = mock(ChangeRequestRecord.class);
        when(mr.id()).thenReturn(MR_ID);
        when(mr.provider()).thenReturn(VersionControlProviderType.GITLAB);
        when(mr.authorId()).thenReturn(7L);
        when(mr.createdAt()).thenReturn(createdAt);
        when(mr.mergedAt()).thenReturn(mergedAt);
        return mr;
    }

    private GroupAnalysisRunDocument groupRun(final String status, final List<GroupAnalysisResponse.Finding> findings,
                                              final List<GroupAnalysisResponse.Member> members,
                                              final Map<String, TokenUsage> usage) {
        final var result = mock(GroupAnalysisResponse.class);
        when(result.groupId()).thenReturn("group-1");
        when(result.groupName()).thenReturn("Payments");
        when(result.runStatus()).thenReturn(status);
        when(result.members()).thenReturn(members);
        when(result.findings()).thenReturn(findings);
        when(result.pipelineId()).thenReturn(77L);
        when(result.createdAt()).thenReturn(1_000L);
        when(result.updatedAt()).thenReturn(4_000L);
        final var run = mock(GroupAnalysisRun.class);
        when(run.id()).thenReturn("group-run-1");
        when(run.invokedBy()).thenReturn("alice");
        when(run.result()).thenReturn(result);
        when(run.reviewPersonas()).thenReturn(List.of(new GroupAnalysisResponse.Persona("security", "Security", "", 1)));
        when(run.usageByExecution()).thenReturn(usage);
        when(groupRuns.get("group-run-1")).thenReturn(Optional.of(run));
        givenEvent(ProjectionQueue.GROUP_ANALYSIS_RUN, "group-run-1", 0);
        service.processPending(5);
        final var captor = ArgumentCaptor.forClass(GroupAnalysisRunDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("group-run-1"), captor.capture());
        return captor.getValue();
    }

    private static GroupAnalysisResponse.Finding finding(final String publicationStatus) {
        final var finding = mock(GroupAnalysisResponse.Finding.class);
        when(finding.publicationStatus()).thenReturn(publicationStatus);
        return finding;
    }

    @Test
    void testProcessPending_shouldIndexCompletedGroupRun_withFindingCountsDurationAndTokens() {
        final var document = groupRun("completed", List.of(finding("posted"), finding("failed"), finding(null)),
                List.of(mock(GroupAnalysisResponse.Member.class), mock(GroupAnalysisResponse.Member.class)),
                Map.of("exec-1", new TokenUsage(100, 20, List.of())));

        assertEquals(GroupAnalysisRunDocument.DOCUMENT_TYPE, document.type());
        assertEquals("group-1", document.groupId());
        assertEquals("alice", document.invokedBy());
        assertEquals(2, document.memberCount());
        assertEquals(List.of("security"), document.reviewPersonaIds());
        assertEquals(3, document.findingCount());
        assertEquals(1, document.postedFindings());
        assertEquals(1, document.failedFindings());
        assertEquals(3_000, document.durationMs());
        assertEquals(120L, document.totalTokens());
        verify(events).delete("event-1");
    }

    @Test
    void testProcessPending_shouldIndexRunningGroupRun_withoutDurationTokensOrFindings() {
        final var document = groupRun("running", null, null, Map.of());

        assertEquals(0, document.memberCount());
        assertEquals(0, document.findingCount());
        assertEquals(0, document.durationMs());
        assertNull(document.totalTokens());
    }

    @Test
    void testProcessPending_shouldIndexQueuedGroupRun_withoutDuration() {
        assertEquals(0, groupRun("queued", List.of(), List.of(), Map.of()).durationMs());
    }

    @Test
    void testProcessPending_shouldIndexMergeRequestWithReviewState_whenReviewExists() {
        final var mr = mergeRequest(1_000, 4_000);
        when(mergeRequests.get(MR_ID)).thenReturn(Optional.of(mr));
        final var data = ReviewStateData.builder().analysisRunId("run-1").analysisOutcome(AnalysisOutcome.PARTIAL)
                .build();
        when(reviewStates.get(List.of(MR_ID))).thenReturn(Map.of(MR_ID,
                new ArnoldReview(MR_ID, ReviewState.REVIEW_READY, data)));
        givenEvent(ProjectionQueue.MERGE_REQUEST, MR_ID, 0);

        assertEquals(1, service.processPending(5));

        final var captor = ArgumentCaptor.forClass(MergeRequestDocument.class);
        verify(es).save(eq(SearchIndexReferences.CHANGE_REQUESTS), eq(MR_ID), captor.capture());
        final var document = captor.getValue();
        assertEquals(3_000, document.mergeDurationMs());
        assertEquals("REVIEW_READY", document.reviewState());
        assertTrue(document.arnoldReviewed());
        assertEquals("run-1", document.latestAnalysisRunId());
        assertEquals("PARTIAL", document.latestAnalysisOutcome());
        verify(events).delete("event-1");
    }

    @Test
    void testProcessPending_shouldLeaveReviewFieldsEmpty_whenMergeRequestUnreviewedAndUnmerged() {
        final var mr = mergeRequest(1_000, 0);
        when(mergeRequests.get(MR_ID)).thenReturn(Optional.of(mr));
        when(reviewStates.get(List.of(MR_ID))).thenReturn(Map.of());
        givenEvent(ProjectionQueue.MERGE_REQUEST, MR_ID, 0);

        service.processPending(5);

        final var captor = ArgumentCaptor.forClass(MergeRequestDocument.class);
        verify(es).save(eq(SearchIndexReferences.CHANGE_REQUESTS), eq(MR_ID), captor.capture());
        assertEquals(0, captor.getValue().mergeDurationMs());
        assertNull(captor.getValue().reviewState());
        assertFalse(captor.getValue().arnoldReviewed());
        assertNull(captor.getValue().latestAnalysisOutcome());
    }

    @Test
    void testProcessPending_shouldFlagMergeRequestAuthorAndPersona_whenCommentIndexed() {
        final var comment = mock(CommentRecord.class);
        when(comment.id()).thenReturn("c1");
        when(comment.changeRequestId()).thenReturn(MR_ID);
        when(comment.authorId()).thenReturn(7L);
        when(comment.persona()).thenReturn(new PersonaAttribution("run-1", "netra", 3));
        when(comments.get("c1")).thenReturn(Optional.of(comment));
        final var mr = mergeRequest(1, 2);
        when(mergeRequests.get(MR_ID)).thenReturn(Optional.of(mr));
        givenEvent(ProjectionQueue.COMMENT, "c1", 0);

        service.processPending(5);

        final var captor = ArgumentCaptor.forClass(CommentDocument.class);
        verify(es).save(eq(SearchIndexReferences.COMMENTS), eq("c1"), captor.capture());
        assertEquals(7L, captor.getValue().mergeRequestAuthorId());
        assertTrue(captor.getValue().byMergeRequestAuthor());
        assertEquals("netra", captor.getValue().personaId());
        assertEquals(3L, captor.getValue().personaVersion());
    }

    @Test
    void testProcessPending_shouldNotFlagAuthor_whenCommentHasNoChangeRequestOrPersona() {
        final var comment = mock(CommentRecord.class);
        when(comment.id()).thenReturn("c2");
        when(comment.authorId()).thenReturn(7L);
        when(comments.get("c2")).thenReturn(Optional.of(comment));
        givenEvent(ProjectionQueue.COMMENT, "c2", 0);

        service.processPending(5);

        final var captor = ArgumentCaptor.forClass(CommentDocument.class);
        verify(es).save(eq(SearchIndexReferences.COMMENTS), eq("c2"), captor.capture());
        assertNull(captor.getValue().mergeRequestAuthorId());
        assertFalse(captor.getValue().byMergeRequestAuthor());
        assertNull(captor.getValue().analysisRunId());
        verify(mergeRequests, never()).get(any(String.class));
    }

    private AnalysisRunDocument projectedRun(final String status, final Map<String, PersonaReviewResult> results) {
        final var run = new AnalysisRun("run-1", MR_ID, List.of("p1", "p2"), "alice", status, results, 1_000, 3_000);
        givenEvent(ProjectionQueue.ANALYSIS_RUN, "run-1", 0);
        when(runs.get("run-1")).thenReturn(Optional.of(run));
        service.processPending(5);
        final var captor = ArgumentCaptor.forClass(AnalysisRunDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("run-1"), captor.capture());
        return captor.getValue();
    }

    @Test
    void testProcessPending_shouldReportSucceeded_whenEveryPersonaSucceeded() {
        final var ok = new PersonaReviewResult("run-1", "p1", PersonaReviewResult.Outcome.SUCCEEDED, null, List.of(),
                null);
        final var ok2 = new PersonaReviewResult("run-1", "p2", PersonaReviewResult.Outcome.SUCCEEDED, null,
                List.of(), null);

        final var document = projectedRun("completed", Map.of("p1", ok, "p2", ok2));

        assertEquals("SUCCEEDED", document.outcome());
        assertNull(document.totalTokens());
    }

    @Test
    void testProcessPending_shouldReportFailedWithoutOutcome_whenRunFailedOrRunning() {
        assertEquals("FAILED", projectedRun("failed", Map.of()).outcome());
    }

    @Test
    void testProcessPending_shouldOmitOutcomeAndDuration_whenRunStillRunning() {
        final var document = projectedRun("running", Map.of());

        assertNull(document.outcome());
        assertEquals(0, document.durationMs());
    }

    @Test
    void testProcessPending_shouldIndexFailedSmartDiff_withoutDurationWhenNotRequested() {
        final var id = SmartDiffRecord.key(VersionControlProviderType.GITLAB, 10, 12, "sha");
        final var diff = new SmartDiffRecord(id, "sha", "exec", "running", null, null, 9_000, null, "queued", 0,
                "alice", null, 0);
        givenEvent(ProjectionQueue.SMART_DIFF, id, 0);
        when(smartDiffs.get(id)).thenReturn(Optional.of(diff));

        service.processPending(5);

        final var captor = ArgumentCaptor.forClass(SmartDiffDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq(id), captor.capture());
        assertEquals(0, captor.getValue().durationMs());
        assertEquals(0, captor.getValue().groupCount());
        assertNull(captor.getValue().harness());
    }

    @Test
    void testProcessPending_shouldHandleSparseProgress_whenNothingRecorded() {
        final var id = "progress:alice:gitlab:10:12:sha";
        final var record = new com.phonepe.arnold.core.model.review.ReviewProgress(id, "sha", null, null,
                null, 5_000);
        final var progress = mock(AerospikeReviewProgressStore.class);
        final var local = new ProjectionServiceImpl(events, mergeRequests, comments, reviewStates, runs, smartDiffs,
                mock(AerospikeGroupAnalysisRunStore.class), progress, mock(AerospikeFlowJobUsageStore.class), es);
        givenEvent(ProjectionQueue.REVIEW_PROGRESS, id, 0);
        when(progress.get(id)).thenReturn(Optional.of(record));

        local.processPending(5);

        final var captor = ArgumentCaptor.forClass(ReviewEngagementDocument.class);
        verify(es).save(eq(SearchIndexReferences.REVIEW_ENGAGEMENT), eq(id), captor.capture());
        assertEquals(0, captor.getValue().filesViewed());
        assertEquals(0, captor.getValue().focusAreasUseful());
    }

    @Test
    void testProcessPending_shouldDropEvent_whenAttemptsExhausted() {
        givenEvent("unknown", "x", 9);

        assertEquals(0, service.processPending(5));

        verify(events).delete("event-1");
        verify(events, never()).update(any(ProjectionEvent.class));
    }

    @Test
    void testProcessPending_shouldSkipExhaustedOrNonPendingEvents_andHonorLimit() {
        when(events.getByRefId("status:pending")).thenReturn(List.of(
                new ProjectionEvent("done", ProjectionQueue.COMMENT, "c", "upsert", "processed", 0, 1, 0),
                new ProjectionEvent("spent", ProjectionQueue.COMMENT, "c", "upsert", "pending", 10, 2, 0)));

        assertEquals(0, service.processPending(5));

        verify(events, never()).delete(any(String.class));
    }

    @Test
    void testMergeRequestKey_shouldRejectMalformedIds() {
        final var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ProjectionServiceImpl.MergeRequestKey.parse("gitlab:10"));

        assertTrue(error.getMessage().contains("gitlab:10"));
        assertEquals(12, ProjectionServiceImpl.MergeRequestKey.parse(MR_ID).number());
    }
}
