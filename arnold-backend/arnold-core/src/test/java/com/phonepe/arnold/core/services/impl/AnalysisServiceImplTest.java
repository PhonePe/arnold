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

import com.phonepe.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.models.analysis.ReviewPersonaDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceImplTest {
    @SuppressWarnings("unchecked")
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine =
            (StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload>) mock(StateMachine.class);

    private AnalysisServiceImpl service(final AerospikeAnalysisRunStore runStore) {
        return new AnalysisServiceImpl(stateMachine, mock(AnalysisOrchestrator.class), runStore,
                mock(com.phonepe.arnold.core.services.PersonaService.class),
                mock(com.phonepe.arnold.core.services.RunnerDispatcher.class),
                mock(com.phonepe.arnold.core.services.ChangeRequestService.class));
    }

    @Test
    void testList_shouldReturnAllRuns_newestFirst() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.list()).thenReturn(List.of(
                new AnalysisRun("older", "gitlab:42:12", List.of("improve"), "owner", "completed", Map.of(), 1L, 2L),
                new AnalysisRun("other", "gitlab:42:13", List.of("improve"), "other-user", "running", Map.of(), 4L, 4L),
                new AnalysisRun("newer", "gitlab:42:14", List.of("improve"), "owner", "running", Map.of(), 3L, 3L)));

        final var runs = service(runStore).list(null, null, 50);

        assertEquals(List.of("other", "newer", "older"), runs.stream().map(response -> response.runId()).toList());
    }

    @Test
    void testList_shouldScopeToMergeRequest_whenRequested() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.list()).thenReturn(List.of(
                new AnalysisRun("mine", "gitlab:42:12", List.of("improve"), "owner", "completed", Map.of(), 1L, 2L),
                new AnalysisRun("theirs", "gitlab:42:12", List.of("improve"), "other-user", "running", Map.of(), 4L, 4L),
                new AnalysisRun("elsewhere", "gitlab:42:13", List.of("improve"), "owner", "running", Map.of(), 3L, 3L)));

        final var runs = service(runStore).list("gitlab:42:12", null, 50);

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
    void testList_shouldHonorBeforeCursorAndLimit() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        when(runStore.list()).thenReturn(java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(createdAt -> new AnalysisRun("run-" + createdAt, "gitlab:42:" + createdAt,
                        List.of("improve"), "owner", "completed", Map.of(), createdAt, createdAt))
                .toList());

        final var page = service(runStore).list(null, 8L, 3);

        assertEquals(List.of("run-7", "run-6", "run-5"), page.stream().map(response -> response.runId()).toList());
    }

    @Test
    void testGet_shouldReturnRunFindings_whenPersonaPublishedComments() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var finding = new com.phonepe.arnold.models.analysis.AnalysisFindingResponse(
                "101", "head", "src/Main.java", "src/Main.java", 12, "Fix this", "posted",
                "https://gitlab.example/project/-/merge_requests/1#note_101");
        final var result = new PersonaReviewResult("run-1", "improve",
                PersonaReviewResult.Outcome.SUCCEEDED, "1 inline suggestion posted", List.of(finding));
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "completed", Map.of("improve", result), 1L, 2L)));

        final var response = service(runStore).get("run-1");

        assertEquals(List.of(finding), response.findings());
    }

    @Test
    void testReviewPersonas_shouldServeRunSnapshot_whenRefsMatch() {
        final var runStore = mock(AerospikeAnalysisRunStore.class);
        final var personas = List.of(new ReviewPersonaSnapshot("netra", "Netra", "Check security.", 3),
                new ReviewPersonaSnapshot("sai", "Sai", "Check bugs.", 1));
        when(runStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:42:12",
                List.of("improve"), "owner", "running", Map.of(), null, null, personas, null, 1L, 2L)));
        when(runStore.get("run-2")).thenReturn(Optional.of(new AnalysisRun("run-2", "gitlab:42:12",
                List.of("improve"), "owner", "completed", Map.of(), null, null, personas, null, 1L, 2L)));
        final var service = service(runStore);

        assertEquals(List.of(new ReviewPersonaDefinition("netra", "Netra", "Check security.", 3),
                new ReviewPersonaDefinition("sai", "Sai", "Check bugs.", 1)), service.reviewPersonas("run-1",
                "netra@3,sai@1"));
        assertEquals(2, service.reviewPersonas("run-1", null).size());
        assertThrows(ArnoldError.class, () -> service.reviewPersonas("run-1", "netra@2,sai@1"));
        assertThrows(ArnoldError.class, () -> service.reviewPersonas("run-2", null));
    }
}
