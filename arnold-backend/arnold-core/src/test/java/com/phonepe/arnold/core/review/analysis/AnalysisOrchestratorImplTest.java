package com.phonepe.arnold.core.review.analysis;

import com.phonepe.arnold.core.services.impl.ProjectionQueue;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.services.AnalysisDispatcher;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
    private final com.phonepe.arnold.core.events.ArnoldSignalPublisher signals =
            mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class);
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
        final var selection = new com.phonepe.arnold.models.analysis.ExecutionOptions("copilot", "selected-model");
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
}
