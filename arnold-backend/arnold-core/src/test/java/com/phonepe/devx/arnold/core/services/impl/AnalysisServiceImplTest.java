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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceImplTest {
    @Test
    void testList_shouldReturnOnlyOwnedRuns_newestFirst() {
        @SuppressWarnings("unchecked")
        final var stateMachine = (StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>)
                mock(StateMachine.class);
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var service = new AnalysisServiceImpl(stateMachine, mock(AnalysisOrchestrator.class), runStore,
                mock(com.phonepe.devx.arnold.core.services.PersonaService.class),
                mock(com.phonepe.devx.arnold.core.services.PipelineTriggerClient.class));
        when(runStore.list()).thenReturn(List.of(
                new AnalysisRun("older", "gitlab:42:12", List.of("improve"), "owner", "completed", Map.of(), 1L, 2L),
                new AnalysisRun("other", "gitlab:42:13", List.of("improve"), "other-user", "running", Map.of(), 4L, 4L),
                new AnalysisRun("newer", "gitlab:42:14", List.of("improve"), "owner", "running", Map.of(), 3L, 3L)));

        final var runs = service.list("owner");

        assertEquals(List.of("newer", "older"), runs.stream().map(response -> response.runId()).toList());
    }

    @Test
    void testGet_shouldHideRun_whenCallerDoesNotOwnIt() {
        @SuppressWarnings("unchecked")
        final var stateMachine = (StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>)
                mock(StateMachine.class);
        final var orchestrator = mock(AnalysisOrchestrator.class);
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var service = new AnalysisServiceImpl(stateMachine, orchestrator, runStore,
                mock(com.phonepe.devx.arnold.core.services.PersonaService.class),
                mock(com.phonepe.devx.arnold.core.services.PipelineTriggerClient.class));
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "running", Map.of(), 1L, 1L)));

        assertThrows(RuntimeException.class, () -> service.get("run-1", "other-user"));
    }

    @Test
    void testGet_shouldReturnRunFindings_whenPersonaPublishedComments() {
        @SuppressWarnings("unchecked")
        final var stateMachine = (StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>)
                mock(StateMachine.class);
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var finding = new com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse(
                "101", "head", "src/Main.java", "src/Main.java", 12, "Fix this", "posted",
                "https://gitlab.example/project/-/merge_requests/1#note_101");
        final var result = new PersonaReviewResult("run-1", "improve",
                PersonaReviewResult.Outcome.SUCCEEDED, "1 inline suggestion posted", List.of(finding));
        final var service = new AnalysisServiceImpl(stateMachine, mock(AnalysisOrchestrator.class), runStore,
                mock(com.phonepe.devx.arnold.core.services.PersonaService.class),
                mock(com.phonepe.devx.arnold.core.services.PipelineTriggerClient.class));
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "completed", Map.of("improve", result), 1L, 2L)));

        final var response = service.get("run-1", "owner");

        assertEquals(List.of(finding), response.findings());
    }
}
