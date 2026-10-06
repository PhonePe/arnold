package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceImplTest {
    @SuppressWarnings("unchecked")
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine =
            (StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>) mock(StateMachine.class);

    private AnalysisServiceImpl service(final AerospikeAnalysisRunStore runStore) {
        return new AnalysisServiceImpl(stateMachine, mock(AnalysisOrchestrator.class), runStore,
                mock(com.phonepe.devx.arnold.core.services.PersonaService.class),
                mock(com.phonepe.devx.arnold.core.services.PipelineTriggerClient.class));
    }

    @Test
    void testList_shouldReturnAllRuns_newestFirst() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.list()).thenReturn(List.of(
                new AnalysisRun("older", "gitlab:42:12", List.of("improve"), "owner", "completed", Map.of(), 1L, 2L),
                new AnalysisRun("other", "gitlab:42:13", List.of("improve"), "other-user", "running", Map.of(), 4L, 4L),
                new AnalysisRun("newer", "gitlab:42:14", List.of("improve"), "owner", "running", Map.of(), 3L, 3L)));

        final var runs = service(runStore).list(null);

        assertEquals(List.of("other", "newer", "older"), runs.stream().map(response -> response.runId()).toList());
    }

    @Test
    void testList_shouldScopeToMergeRequest_whenRequested() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.list()).thenReturn(List.of(
                new AnalysisRun("mine", "gitlab:42:12", List.of("improve"), "owner", "completed", Map.of(), 1L, 2L),
                new AnalysisRun("theirs", "gitlab:42:12", List.of("improve"), "other-user", "running", Map.of(), 4L, 4L),
                new AnalysisRun("elsewhere", "gitlab:42:13", List.of("improve"), "owner", "running", Map.of(), 3L, 3L)));

        final var runs = service(runStore).list("gitlab:42:12");

        assertEquals(List.of("theirs", "mine"), runs.stream().map(response -> response.runId()).toList());
    }

    @Test
    void testGet_shouldReturnRun_whenCallerDidNotInvokeIt() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "running", Map.of(), 1L, 1L)));

        assertEquals("run-1", service(runStore).get("run-1").runId());
    }

    @Test
    void testGet_shouldReturnRunFindings_whenPersonaPublishedComments() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var finding = new com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse(
                "101", "head", "src/Main.java", "src/Main.java", 12, "Fix this", "posted",
                "https://gitlab.example/project/-/merge_requests/1#note_101");
        final var result = new PersonaReviewResult("run-1", "improve",
                PersonaReviewResult.Outcome.SUCCEEDED, "1 inline suggestion posted", List.of(finding));
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "completed", Map.of("improve", result), 1L, 2L)));

        final var response = service(runStore).get("run-1");

        assertEquals(List.of(finding), response.findings());
    }
}
