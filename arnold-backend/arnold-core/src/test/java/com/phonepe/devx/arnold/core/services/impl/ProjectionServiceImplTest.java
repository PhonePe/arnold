package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewProgress;
import com.phonepe.devx.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.devx.arnold.core.storage.ReviewStateStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.models.es.AnalysisFindingDocument;
import com.phonepe.devx.arnold.models.es.AnalysisRunDocument;
import com.phonepe.devx.arnold.models.es.ReviewEngagementDocument;
import com.phonepe.devx.arnold.models.es.SmartDiffDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectionServiceImplTest {
    private final AerospikeProjectionEventStore events = mock(AerospikeProjectionEventStore.class);
    private final AerospikeAnalysisRunStore runs = mock(AerospikeAnalysisRunStore.class);
    private final AerospikeSmartDiffStore smartDiffs = mock(AerospikeSmartDiffStore.class);
    private final AerospikeReviewProgressStore progress = mock(AerospikeReviewProgressStore.class);
    private final com.phonepe.devx.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore usages
            = mock(com.phonepe.devx.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore.class);
    private final ESStorageManager es = mock(ESStorageManager.class);
    private final ProjectionServiceImpl service = new ProjectionServiceImpl(events,
            mock(AerospikeChangeRequestStore.class), mock(AerospikeCommentStore.class),
            mock(ReviewStateStore.class), runs, smartDiffs, progress, usages, es);

    @Test
    void testProcessPending_shouldIndexRunSummary_whenAnalysisRunCompletedPartially() {
        final var finding = new AnalysisFindingResponse("f1", "sha", "a.java", null, 1, "body", "posted", null, "p1", 3L);
        final var run = new AnalysisRun("run-1", "gitlab:10:12", List.of("p1", "p2"), "alice", "completed",
                Map.of("p1", new PersonaReviewResult("run-1", "p1", PersonaReviewResult.Outcome.SUCCEEDED, null,
                                List.of(finding), new com.phonepe.devx.arnold.models.analysis.TokenUsage(90, 10, List.of())),
                        "p2", new PersonaReviewResult("run-1", "p2", PersonaReviewResult.Outcome.FAILED, "boom")),
                7L, null, List.of(new ReviewPersonaSnapshot("p1", "Netra", "x", 3)), "fp", 1_000, 4_000,
                new ExecutionOptions("copilot", "gpt"));
        givenEvent(ProjectionQueue.ANALYSIS_RUN, "run-1");
        when(runs.get("run-1")).thenReturn(Optional.of(run));

        assertEquals(1, service.processPending(10));

        final var captor = ArgumentCaptor.forClass(AnalysisRunDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("run-1"), captor.capture());
        final var document = captor.getValue();
        assertEquals("PARTIAL", document.outcome());
        assertEquals("10", document.repositoryId());
        assertEquals(12, document.number());
        assertEquals(List.of("p1@3"), document.personaVersions());
        assertEquals(1, document.succeededPersonas());
        assertEquals(1, document.failedPersonas());
        assertEquals(1, document.postedFindings());
        assertEquals(3_000, document.durationMs());
        assertEquals(List.of("p1"), document.reviewPersonaIds());
        assertEquals(100L, document.totalTokens());
        final var findingCaptor = ArgumentCaptor.forClass(AnalysisFindingDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("finding:run-1:f1"), findingCaptor.capture());
        assertEquals("p1", findingCaptor.getValue().personaId());
        assertEquals(3L, findingCaptor.getValue().personaVersion());
        assertEquals("posted", findingCaptor.getValue().publicationStatus());
        verify(events).delete("event-1");
    }

    @Test
    void testProcessPending_shouldIndexSmartDiffDuration_whenSmartDiffCompleted() {
        final var id = SmartDiffRecord.key(10, 12, "sha");
        final var diff = new SmartDiffRecord(id, "sha", "exec", "completed", "summary", List.of(), 9_000, null,
                "completed", 4, "alice", new ExecutionOptions("copilot", null), 2_000);
        givenEvent(ProjectionQueue.SMART_DIFF, id);
        when(smartDiffs.get(id)).thenReturn(Optional.of(diff));

        service.processPending(10);

        final var captor = ArgumentCaptor.forClass(SmartDiffDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq(id), captor.capture());
        assertEquals("gitlab:10:12", captor.getValue().mergeRequestId());
        assertEquals(7_000, captor.getValue().durationMs());
        assertEquals(4, captor.getValue().assessedCommentCount());
    }

    @Test
    void testProcessPending_shouldIndexJobAndPerModelUsage_whenFlowJobUsageReported() {
        final var usage = new com.phonepe.devx.arnold.models.analysis.TokenUsage(30, 6, List.of(
                new com.phonepe.devx.arnold.models.analysis.TokenUsage.Model("a", 10, 2),
                new com.phonepe.devx.arnold.models.analysis.TokenUsage.Model("b", 20, 4)), "https://gitlab/jobs/1");
        givenEvent(ProjectionQueue.FLOW_JOB_USAGE, "improve:run-1");
        when(usages.get("improve:run-1")).thenReturn(Optional.of(new com.phonepe.devx.arnold.core.model.analysis
                .FlowJobUsage("improve:run-1", "improve", "run-1", "gitlab:10:12", "alice", "copilot", "succeeded",
                usage, 1_000, 2_000)));

        service.processPending(10);

        final var job = ArgumentCaptor.forClass(com.phonepe.devx.arnold.models.es.FlowJobUsageDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("usage:improve:run-1"), job.capture());
        assertEquals(36, job.getValue().totalTokens());
        assertEquals(List.of("a", "b"), job.getValue().models());
        final var model = ArgumentCaptor.forClass(com.phonepe.devx.arnold.models.es.FlowJobModelUsageDocument.class);
        verify(es).save(eq(SearchIndexReferences.ANALYSIS_RUNS), eq("usage:improve:run-1:b"), model.capture());
        assertEquals(24, model.getValue().totalTokens());
    }

    @Test
    void testProcessPending_shouldIndexEngagement_whenReviewProgressUpdated() {
        final var id = ReviewProgress.key("alice", 10, 12, "sha");
        final var record = new ReviewProgress(id, "sha", List.of("a", "b"), List.of("s1"),
                Map.of("x", "useful", "y", "not-useful", "z", "useful"), 5_000);
        givenEvent(ProjectionQueue.REVIEW_PROGRESS, id);
        when(progress.get(id)).thenReturn(Optional.of(record));

        service.processPending(10);

        final var captor = ArgumentCaptor.forClass(ReviewEngagementDocument.class);
        verify(es).save(eq(SearchIndexReferences.REVIEW_ENGAGEMENT), eq(id), captor.capture());
        final var document = captor.getValue();
        assertEquals("alice", document.userId());
        assertEquals("gitlab:10:12", document.mergeRequestId());
        assertEquals(2, document.filesViewed());
        assertEquals(2, document.focusAreasUseful());
        assertEquals(1, document.focusAreasNotUseful());
    }

    @Test
    void testProcessPending_shouldRetry_whenRecordIsMissing() {
        givenEvent(ProjectionQueue.ANALYSIS_RUN, "run-x");
        when(runs.get("run-x")).thenReturn(Optional.empty());

        assertEquals(0, service.processPending(10));

        verify(es, never()).save(any(), any(), any());
        verify(events).update(any(ProjectionEvent.class));
    }

    private void givenEvent(final String type, final String aggregateId) {
        when(events.getByRefId("status:pending")).thenReturn(List.of(
                new ProjectionEvent("event-1", type, aggregateId, "upsert", "pending", 0, 1, 0)));
    }
}
