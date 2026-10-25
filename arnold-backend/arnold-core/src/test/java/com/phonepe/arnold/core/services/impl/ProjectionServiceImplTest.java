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
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.arnold.core.model.review.ReviewProgress;
import com.phonepe.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.arnold.models.analysis.AnalysisFindingResponse;
import com.phonepe.arnold.models.analysis.ExecutionOptions;
import com.phonepe.arnold.models.es.AnalysisRunDocument;
import com.phonepe.arnold.models.es.ReviewEngagementDocument;
import com.phonepe.arnold.models.es.SmartDiffDocument;
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
    private final ESStorageManager es = mock(ESStorageManager.class);
    private final ProjectionServiceImpl service = new ProjectionServiceImpl(events,
            mock(AerospikeChangeRequestStore.class), mock(AerospikeCommentStore.class),
            mock(ReviewStateStore.class), runs, smartDiffs, progress, es);

    @Test
    void testProcessPending_shouldIndexRunSummary_whenAnalysisRunCompletedPartially() {
        final var finding = new AnalysisFindingResponse("f1", "sha", "a.java", null, 1, "body", "posted", null);
        final var run = new AnalysisRun("run-1", "gitlab:10:12", List.of("p1", "p2"), "alice", "completed",
                Map.of("p1", new PersonaReviewResult("run-1", "p1", PersonaReviewResult.Outcome.SUCCEEDED, null,
                                List.of(finding)),
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
        verify(events).delete("event-1");
    }

    @Test
    void testProcessPending_shouldIndexSmartDiffDuration_whenSmartDiffCompleted() {
        final var id = SmartDiffRecord.key(VersionControlProviderType.GITLAB, 10, 12, "sha");
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
    void testProcessPending_shouldIndexEngagement_whenReviewProgressUpdated() {
        final var id = ReviewProgress.key(VersionControlProviderType.GITLAB, "alice", 10, 12, "sha");
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
