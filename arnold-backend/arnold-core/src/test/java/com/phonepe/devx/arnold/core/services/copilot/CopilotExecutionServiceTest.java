package com.phonepe.devx.arnold.core.services.copilot;

import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.copilot.CopilotExecutionGrant;
import com.phonepe.devx.arnold.core.storage.aerospike.*;
import com.phonepe.devx.arnold.models.integration.CopilotExecutionTokenResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CopilotExecutionServiceTest {
    @Test
    void grantsAreHashedScopedExpiringAndRevocable() {
        final var config = new ArnoldCoreConfiguration();
        config.getCopilotConfiguration().setEnabled(true);
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(true);
        final var connections = mock(CopilotConnectionService.class);
        final var grants = mock(AerospikeCopilotExecutionGrantStore.class);
        final var runs = mock(AerospikeAnalysisRunStore.class);
        final var service = new CopilotExecutionService(config, connections, grants, runs,
                mock(AerospikeGroupAnalysisRunStore.class), mock(AerospikeSmartDiffStore.class), mock(com.phonepe.devx.arnold.core.services.AnalysisExecutionService.class));
        final var run = mock(AnalysisRun.class);
        when(run.id()).thenReturn("run");
        when(run.invokedBy()).thenReturn("alice");
        when(run.createdAt()).thenReturn(System.currentTimeMillis());
        when(run.status()).thenReturn("running");
        when(run.execution()).thenReturn(new com.phonepe.devx.arnold.models.analysis.ExecutionOptions("copilot", "selected-model"));
        when(runs.get("run")).thenReturn(Optional.of(run));
        when(connections.executionConnectionId("alice")).thenReturn("connection");
        when(connections.executionToken("alice", "connection"))
                .thenReturn(new CopilotExecutionTokenResponse("oauth-secret", null));
        final var variables = service.pipelineVariables(Map.of("WORKFLOW", "improve", "ARNOLD_RUN_ID", "run"));
        final var key = variables.get(CopilotExecutionService.KEY_VARIABLE);
        assertEquals(43, key.length());
        assertEquals("copilot", variables.get("CLIO_HARNESS"));
        assertEquals("selected-model", variables.get("CLIO_LLM_MODEL"));
        assertFalse(variables.containsValue("oauth-secret"));
        final var saved = ArgumentCaptor.forClass(CopilotExecutionGrant.class);
        verify(grants).create(saved.capture());
        final var grant = saved.getValue();
        assertEquals(CopilotExecutionService.hash(key), grant.id());
        assertNotEquals(key, grant.id());
        when(grants.get(grant.id())).thenReturn(Optional.of(grant));
        assertEquals("oauth-secret", service.exchange(key).accessToken());
        when(run.status()).thenReturn("completed");
        assertThrows(ArnoldError.class, () -> service.exchange(key));
        when(run.status()).thenReturn("running");
        when(run.invokedBy()).thenReturn("bob");
        assertThrows(ArnoldError.class, () -> service.exchange(key));
        when(run.invokedBy()).thenReturn("alice");
        when(grants.get(grant.id())).thenReturn(Optional.of(new CopilotExecutionGrant(grant.id(), "alice",
                "connection", "improve", "run", "run", 0)));
        assertThrows(ArnoldError.class, () -> service.exchange(key));
        assertThrows(ArnoldError.class, () -> service.exchange("wrong"));
        service.revoke(variables);
        verify(grants).delete(grant.id());
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(false);
        assertEquals(Map.of("WORKFLOW", "smart_diff"), service.pipelineVariables(
                Map.of("WORKFLOW", "smart_diff", "ARNOLD_COPILOT_OWNER", "alice")));
        assertThrows(ArnoldError.class, () -> service.exchange(key));
    }
    @Test
    void retriesInvalidateGroupAndSmartDiffKeys() {
        final var config = new ArnoldCoreConfiguration();
        config.getCopilotConfiguration().setEnabled(true);
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(true);
        final var connections = mock(CopilotConnectionService.class);
        final var grants = mock(AerospikeCopilotExecutionGrantStore.class);
        final var groups = mock(AerospikeGroupAnalysisRunStore.class);
        final var stories = mock(AerospikeSmartDiffStore.class);
        final var service = new CopilotExecutionService(config, connections, grants,
                mock(AerospikeAnalysisRunStore.class), groups, stories, mock(com.phonepe.devx.arnold.core.services.AnalysisExecutionService.class));
        when(connections.executionConnectionId("alice")).thenReturn("connection");
        when(connections.executionToken("alice", "connection"))
                .thenReturn(new CopilotExecutionTokenResponse("token", null));
        final var group = mock(com.phonepe.devx.arnold.core.model.analysis.GroupAnalysisRun.class);
        final var result = mock(com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse.class);
        when(group.id()).thenReturn("group");
        when(group.invokedBy()).thenReturn("alice");
        when(group.result()).thenReturn(result);
        when(group.executionStartedAt()).thenReturn(System.currentTimeMillis());
        when(result.executionId()).thenReturn("execution");
        when(result.runStatus()).thenReturn("queued");
        when(groups.get("group")).thenReturn(Optional.of(group));
        final var story = mock(com.phonepe.devx.arnold.core.model.review.SmartDiffRecord.class);
        when(story.executionId()).thenReturn("execution");
        when(story.status()).thenReturn("running");
        when(story.updatedAt()).thenReturn(System.currentTimeMillis());
        when(stories.get(com.phonepe.devx.arnold.core.model.review.SmartDiffRecord.key(10, 12, "head")))
                .thenReturn(Optional.of(story));
        for (final var variables : java.util.List.of(
                Map.of("WORKFLOW", "group_review", "ARNOLD_RUN_ID", "group", "EXECUTION_ID", "execution"),
                Map.of("WORKFLOW", "smart_diff", "PROJECT_ID", "10", "MR_IID", "12", "EXPECTED_HEAD_SHA", "head",
                        "EXECUTION_ID", "execution", "ARNOLD_COPILOT_OWNER", "alice"))) {
            final var issued = service.pipelineVariables(variables);
            assertFalse(issued.containsKey("ARNOLD_COPILOT_OWNER"));
            final var key = issued.get(CopilotExecutionService.KEY_VARIABLE);
            final var saved = ArgumentCaptor.forClass(CopilotExecutionGrant.class);
            verify(grants, atLeastOnce()).create(saved.capture());
            final var grant = saved.getValue();
            when(grants.get(grant.id())).thenReturn(Optional.of(grant));
            assertEquals("token", service.exchange(key).accessToken());
            if ("group_review".equals(grant.workflow())) when(result.executionId()).thenReturn("retry");
            else when(story.executionId()).thenReturn("retry");
            assertThrows(ArnoldError.class, () -> service.exchange(key));
        }
    }

    @Test
    void explicitOpenCodeSelectionDoesNotIssueCopilotCredentials() {
        final var config = new ArnoldCoreConfiguration();
        config.getCopilotConfiguration().setEnabled(true);
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(true);
        final var connections = mock(CopilotConnectionService.class);
        final var grants = mock(AerospikeCopilotExecutionGrantStore.class);
        final var runs = mock(AerospikeAnalysisRunStore.class);
        final var groups = mock(AerospikeGroupAnalysisRunStore.class);
        final var service = new CopilotExecutionService(config, connections, grants, runs,
                groups, mock(AerospikeSmartDiffStore.class), mock(com.phonepe.devx.arnold.core.services.AnalysisExecutionService.class));
        final var selection = new com.phonepe.devx.arnold.models.analysis.ExecutionOptions("opencode", "godric/model");
        final var run = mock(AnalysisRun.class);
        when(run.execution()).thenReturn(selection);
        when(runs.get("run")).thenReturn(Optional.of(run));
        final var group = mock(com.phonepe.devx.arnold.core.model.analysis.GroupAnalysisRun.class);
        when(group.context()).thenReturn(new com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse.StartRequest(
                null, null, null, null, selection));
        when(groups.get("run")).thenReturn(Optional.of(group));
        for (var enabled : java.util.List.of(true, false)) {
            config.getCopilotConfiguration().setJobTokenExchangeEnabled(enabled);
            for (var workflow : java.util.List.of("improve", "group_review")) {
                final var variables = service.pipelineVariables(Map.of("WORKFLOW", workflow, "ARNOLD_RUN_ID", "run"));
                assertEquals("opencode", variables.get("CLIO_HARNESS"));
                assertEquals("godric/model", variables.get("CLIO_OPENCODE_MODEL"));
                assertEquals("true", variables.get("CLIO_DISABLE_FALLBACK"));
                assertFalse(variables.containsKey(CopilotExecutionService.KEY_VARIABLE));
            }
        }
        verifyNoInteractions(connections, grants);
        when(run.execution()).thenReturn(new com.phonepe.devx.arnold.models.analysis.ExecutionOptions("copilot", "model"));
        assertThrows(ArnoldError.class, () -> service.pipelineVariables(Map.of("WORKFLOW", "improve", "ARNOLD_RUN_ID", "run")));
    }

}
