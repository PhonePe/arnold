package com.phonepe.devx.arnold.core.review.analysis;

import com.phonepe.devx.arnold.core.services.impl.ProjectionQueue;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.services.AnalysisDispatcher;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisOrchestratorImplTest {
    private final AerospikeAnalysisRunStore runStore = mock(AerospikeAnalysisRunStore.class);
    private final AnalysisDispatcher dispatcher = mock(AnalysisDispatcher.class);
    @SuppressWarnings("unchecked")
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine =
            mock(StateMachine.class);
    private final AtomicReference<AnalysisRun> stored = new AtomicReference<>();
    private final com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher signals =
            mock(com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher.class);
    private final AnalysisOrchestratorImpl orchestrator = new AnalysisOrchestratorImpl(runStore, dispatcher,
            stateMachine, signals, mock(ProjectionQueue.class));

    @BeforeEach
    void setUp() {
        when(runStore.list()).thenReturn(List.of());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(runStore).create(any(AnalysisRun.class));
        when(runStore.getWithVersion(anyString())).thenAnswer(invocation ->
                java.util.Optional.of(Versioned.of(stored.get(), 1L)));
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(runStore).checkAndUpdate(any(AnalysisRun.class), any());
    }

    @Test
    void executionSelectionSurvivesDispatchAndCompletion() {
        final var selection = new com.phonepe.devx.arnold.models.analysis.ExecutionOptions("copilot", "selected-model");
        orchestrator.startAnalysis("gitlab:42:12", new ReviewEventPayload.InvokeAnalysis(List.of("improve"), "user-1",
                null, List.of(), null, List.of(), "fingerprint", selection));
        assertEquals(selection, stored.get().execution());
        orchestrator.recordPersonaCompletion("gitlab:42:12", new PersonaReviewResult(stored.get().id(), "improve",
                PersonaReviewResult.Outcome.SUCCEEDED, null));
        assertEquals(selection, stored.get().execution());
    }

    @Test
    void testStartAnalysis_shouldPersistPipelineId_whenDispatchSucceeds() {
        when(dispatcher.dispatch(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(91L);

        orchestrator.startAnalysis("gitlab:42:12",
                new ReviewEventPayload.InvokeAnalysis(List.of("improve"), "user-1"));

        assertEquals(91L, stored.get().pipelineId());
        assertEquals("running", stored.get().status());
    }

    @Test
    void testStartAnalysis_shouldFailTimedOutRunAndDispatchNewOne_whenPreviousRunNeverReported() {
        final var stuck = new AnalysisRun("stuck", "gitlab:42:12", List.of("improve"), "user-1", "running", Map.of(), 1, 1);
        stored.set(stuck);
        when(runStore.list()).thenReturn(List.of(stuck));
        final var updates = new java.util.ArrayList<AnalysisRun>();
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(0));
            stored.set(invocation.getArgument(0));
            return null;
        }).when(runStore).checkAndUpdate(any(AnalysisRun.class), any());

        final var runId = orchestrator.startAnalysis("gitlab:42:12",
                new ReviewEventPayload.InvokeAnalysis(List.of("improve"), "user-1"));

        assertNotEquals("stuck", runId);
        assertEquals("stuck", updates.get(0).id());
        assertEquals("failed", updates.get(0).status());
        verify(dispatcher).dispatch(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void testOnPipelineFinished_shouldFailRunAndCompleteMachine_whenPipelineFailed() {
        final var run = new AnalysisRun("r1", "gitlab:42:12", List.of("improve"), "user-1", "running", Map.of(), 91L,
                null, List.of(), null, 1, 1);
        stored.set(run);
        when(runStore.list()).thenReturn(List.of(run));

        orchestrator.onPipelineFinished(91L, "failed");

        assertEquals("failed", stored.get().status());
        assertEquals("Clio pipeline failed", stored.get().failureDetail());
        verify(stateMachine).tryFire(anyString(), any());
    }

    @Test
    void testOnPipelineFinished_shouldIgnore_whenPipelineSucceededOrUnknown() {
        final var run = new AnalysisRun("r1", "gitlab:42:12", List.of("improve"), "user-1", "running", Map.of(), 91L,
                null, List.of(), null, 1, 1);
        when(runStore.list()).thenReturn(List.of(run));

        orchestrator.onPipelineFinished(91L, "success");
        orchestrator.onPipelineFinished(92L, "failed");

        verify(runStore, never()).checkAndUpdate(any(AnalysisRun.class), any());
    }

    @Test
    void testIsAbandoned_shouldOnlyReleaseRunsThatCannotComplete() {
        final var now = System.currentTimeMillis();
        when(runStore.get("live")).thenReturn(java.util.Optional.of(
                new AnalysisRun("live", "mr", List.of("improve"), "u", "running", Map.of(), now, now)));
        when(runStore.get("stuck")).thenReturn(java.util.Optional.of(
                new AnalysisRun("stuck", "mr", List.of("improve"), "u", "running", Map.of(), 1, 1)));
        when(runStore.get("failed")).thenReturn(java.util.Optional.of(
                new AnalysisRun("failed", "mr", List.of("improve"), "u", "failed", Map.of(), now, now)));
        when(runStore.get("missing")).thenReturn(java.util.Optional.empty());

        assertFalse(orchestrator.isAbandoned("live"));
        assertTrue(orchestrator.isAbandoned("stuck"));
        assertTrue(orchestrator.isAbandoned("failed"));
        assertTrue(orchestrator.isAbandoned("missing"));
    }

    @Test
    void testStartAnalysis_shouldMarkRunFailed_whenDispatchFails() {
        when(dispatcher.dispatch(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("failure"));

        assertThrows(RuntimeException.class, () -> orchestrator.startAnalysis("gitlab:42:12",
                new ReviewEventPayload.InvokeAnalysis(List.of("improve"), "user-1")));

        assertEquals("failed", stored.get().status());
        assertEquals("Could not trigger Clio Improve", stored.get().failureDetail());
    }

    @Test
    void testRecordPersonaCompletion_shouldRetryNotification_whenDeliveryFailsAfterCompletion() {
        stored.set(new AnalysisRun("run", "gitlab:42:12", List.of("improve"), "user-1", "running", Map.of(), 1, 1));
        final var result = new PersonaReviewResult("run", "improve", PersonaReviewResult.Outcome.SUCCEEDED, null);
        doThrow(new IllegalStateException("Inbox unavailable")).doNothing()
                .when(signals).publish(any(NotificationRequested.class));

        assertThrows(IllegalStateException.class, () -> orchestrator.recordPersonaCompletion("gitlab:42:12", result));
        assertEquals("completed", stored.get().status());
        final var completed = stored.get();
        orchestrator.recordPersonaCompletion("gitlab:42:12", result);

        final var notifications = org.mockito.ArgumentCaptor.forClass(NotificationRequested.class);
        verify(signals, times(2)).publish(notifications.capture());
        assertEquals(notifications.getAllValues().get(0).id(), notifications.getAllValues().get(1).id());
        assertEquals(completed, stored.get());
        verify(runStore).checkAndUpdate(any(AnalysisRun.class), any());
        verify(stateMachine).tryFire(anyString(), any());
    }

    @Test
    void testRecordPersonaCompletion_shouldNotNotify_whenDuplicateArrivesBeforeRunCompletes() {
        final var result = new PersonaReviewResult("run", "first", PersonaReviewResult.Outcome.SUCCEEDED, null);
        stored.set(new AnalysisRun("run", "gitlab:42:12", List.of("first", "second"), "user-1", "running",
                Map.of("first", result), 1, 1));

        orchestrator.recordPersonaCompletion("gitlab:42:12", result);

        verifyNoInteractions(signals, stateMachine);
        assertEquals("running", stored.get().status());
    }

    @Test
    void testAttributed_shouldKeepSnapshotPersonasOnly_andUseSnapshotVersion() {
        final var run = new AnalysisRun("run", "gitlab:42:12", List.of("improve"), "user-1", "running", Map.of(),
                null, null, List.of(new com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot("netra",
                "Netra", "Check security.", 3)), null, 1L, 1L);
        final var known = new com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse("1", "head", "a.java",
                "a.java", 1, "x", "posted", null, "netra", 1L);
        final var unknown = new com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse("2", "head", "a.java",
                "a.java", 2, "y", "posted", null, "ghost", 1L);
        final var general = new com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse("3", "head", "a.java",
                "a.java", 3, "z", "posted", null);

        final var findings = AnalysisOrchestratorImpl.attributed(run, new PersonaReviewResult("run", "improve",
                PersonaReviewResult.Outcome.SUCCEEDED, null, List.of(known, unknown, general))).findings();

        assertEquals("netra", findings.get(0).personaId());
        assertEquals(3L, findings.get(0).personaVersion());
        assertEquals(null, findings.get(1).personaId());
        assertEquals(null, findings.get(1).personaVersion());
        assertEquals(general, findings.get(2));
    }
}
