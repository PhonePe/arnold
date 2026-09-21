package com.phonepe.devx.arnold.core.review.analysis;

import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
            stateMachine, signals);

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
}
