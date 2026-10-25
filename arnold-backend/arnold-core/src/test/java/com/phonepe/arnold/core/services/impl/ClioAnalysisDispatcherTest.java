package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClioAnalysisDispatcherTest {
    private final RunnerDispatcher gitLabClient = mock(RunnerDispatcher.class);
    private final AerospikeChangeRequestStore changeRequestStore = mock(AerospikeChangeRequestStore.class);
    private final ClioAnalysisDispatcher dispatcher = new ClioAnalysisDispatcher(gitLabClient,
            changeRequestStore, new ObjectMapper());

    @Test
    void testDispatch_shouldTriggerImprovePipeline_whenMergeRequestExists() {
        final var mergeRequest = mock(ChangeRequestRecord.class);
        when(mergeRequest.webUrl()).thenReturn("https://gitlab.example/team/repo/-/merge_requests/12");
        when(changeRequestStore.get("gitlab:42:12")).thenReturn(Optional.of(mergeRequest));
        when(gitLabClient.submit(eq(Map.of(
                "WORKFLOW", "improve",
                "MR_LINK", "https://gitlab.example/team/repo/-/merge_requests/12",
                "ARNOLD_RUN_ID", "run-1",
                "ARNOLD_MERGE_REQUEST_ID", "gitlab:42:12",
                "RFC_URL", "https://docs.example/rfc",
                "JIRA_TICKETS", "ACME-123,ACME-456",
                "REVIEW_CONTEXT", "Focus on retry behavior.",
                "REVIEW_PERSONAS_JSON", "[{\"id\":\"netra\",\"name\":\"Netra\",\"instructions\":\"Check security.\",\"version\":1}]"
        )))).thenReturn(99L);

        final var pipelineId = dispatcher.dispatch("gitlab:42:12", "run-1", "improve",
                "https://docs.example/rfc", java.util.List.of("ACME-123", "ACME-456"),
                "Focus on retry behavior.", List.of(new ReviewPersonaSnapshot("netra", "Netra",
                        "Check security.", 1)));

        assertEquals(99L, pipelineId);
        verify(gitLabClient).submit(eq(Map.of(
                "WORKFLOW", "improve",
                "MR_LINK", "https://gitlab.example/team/repo/-/merge_requests/12",
                "ARNOLD_RUN_ID", "run-1",
                "ARNOLD_MERGE_REQUEST_ID", "gitlab:42:12",
                "RFC_URL", "https://docs.example/rfc",
                "JIRA_TICKETS", "ACME-123,ACME-456",
                "REVIEW_CONTEXT", "Focus on retry behavior.",
                "REVIEW_PERSONAS_JSON", "[{\"id\":\"netra\",\"name\":\"Netra\",\"instructions\":\"Check security.\",\"version\":1}]"
        )));
    }

    @Test
    void testDispatch_shouldRejectUnsupportedPersona_whenPersonaIsNotImprove() {
        assertThrows(RuntimeException.class,
                () -> dispatcher.dispatch("gitlab:42:12", "run-1", "security", null, java.util.List.of(), null,
                        List.of()));
    }
}
