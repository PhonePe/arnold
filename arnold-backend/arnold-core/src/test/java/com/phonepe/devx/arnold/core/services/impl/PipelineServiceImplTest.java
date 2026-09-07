package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.PipelineConfig;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenRequest;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenerationResult;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationCompletionRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationStatus;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.phonepe.devx.arnold.core.services.RuleGenerationExecutionStore;
import com.phonepe.devx.arnold.models.request.TargetScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PipelineServiceImplTest {

    @Mock
    private PipelineTriggerClient gitLabClient;
    @Mock
    private RuleGenerationExecutionStore executionStore;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void shouldThrow_whenProjectIdIsBlank() {
        final var config = PipelineConfig.builder().build();

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineServiceImpl(gitLabClient, config, executionStore));
    }

    @Test
    void shouldThrow_whenPipelineRefIsNull() {
        final var config = PipelineConfig.builder()
                .clioProjectId("123")
                .clioPipelineRef(null)
                .build();

        assertThrows(NullPointerException.class,
                () -> new PipelineServiceImpl(gitLabClient, config, executionStore));
    }

    @Test
    void shouldThrow_whenPipelineRefIsBlank() {
        final var config = PipelineConfig.builder()
                .clioProjectId("123")
                .clioPipelineRef(" ")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> new PipelineServiceImpl(gitLabClient, config, executionStore));
    }

    @Test
    void shouldTriggerPipeline_withDefaultScope_whenScopeIsNull() {
        final var config = PipelineConfig.builder()
                .clioProjectId("123")
                .build();
        final var service = new PipelineServiceImpl(gitLabClient, config, executionStore);
        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .scope(null)
                .build();
        when(gitLabClient.triggerPipeline(eq("123"), eq("master"), any()))
                .thenReturn(42L);

        final var result = service.triggerRuleGeneration(request);

        assertEquals(42L, result.getPipelineId());
        verify(gitLabClient).triggerPipeline(
                eq("123"),
                eq("master"),
                org.mockito.ArgumentMatchers.argThat(variables ->
                        "rules_from_feedback".equals(variables.get("WORKFLOW"))
                                && "devx/checkout-api".equals(variables.get("TARGET_PATH"))
                                && TargetScope.GROUP.name().equals(variables.get("SCOPE"))
                                && result.getExecutionId().equals(variables.get("ARNOLD_EXECUTION_ID"))));
    }

    @Test
    void shouldTriggerPipeline_withProvidedScope() {
        final var config = PipelineConfig.builder()
                .clioProjectId("123")
                .build();
        final var service = new PipelineServiceImpl(gitLabClient, config, executionStore);
        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .scope(TargetScope.PROJECT)
                .daysBack(14)
                .minScore(0.8)
                .build();
        when(gitLabClient.triggerPipeline(eq("123"), eq("master"), any()))
                .thenReturn(99L);

        final var result = service.triggerRuleGeneration(request);

        assertEquals(99L, result.getPipelineId());
        verify(gitLabClient).triggerPipeline(
                eq("123"),
                eq("master"),
                org.mockito.ArgumentMatchers.argThat(variables ->
                        TargetScope.PROJECT.name().equals(variables.get("SCOPE"))
                                && "14".equals(variables.get("DAYS_BACK"))
                                && "0.8".equals(variables.get("MIN_SCORE"))));
    }

    @Test
    void shouldUseConfiguredRef_whenNotMain() {
        final var config = PipelineConfig.builder()
                .clioProjectId("123")
                .clioPipelineRef("develop")
                .build();
        final var service = new PipelineServiceImpl(gitLabClient, config, executionStore);
        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .build();
        when(gitLabClient.triggerPipeline(eq("123"), eq("develop"), any()))
                .thenReturn(7L);

        final var result = service.triggerRuleGeneration(request);

        assertEquals(7L, result.getPipelineId());
        verify(gitLabClient).triggerPipeline(eq("123"), eq("develop"), any());
    }

    @Test
    void shouldNotTriggerPipeline_whenInitialExecutionSaveFails() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        org.mockito.Mockito.doThrow(new RuntimeException("ES unavailable"))
                .when(executionStore).save(any());

        assertThrows(RuntimeException.class, () -> service.triggerRuleGeneration(
                TriggerRuleGenRequest.builder().targetPath("devx").build()));

        verify(gitLabClient, never()).triggerPipeline(any(), any(), any());
    }

    @Test
    void shouldCompleteTriggeredExecution() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        final var existing = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .targetPath("devx")
                .scope(TargetScope.GROUP)
                .status(RuleGenerationStatus.TRIGGERED)
                .createdAt(1L)
                .updatedAt(1L)
                .build();
        when(executionStore.get("exec-1")).thenReturn(Optional.of(existing));

        service.completeRuleGeneration("exec-1", RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.SUCCEEDED)
                .mrUrl("https://gitlab/mr/1")
                .rulesGenerated(2)
                .build());

        final var captor = ArgumentCaptor.forClass(RuleGenerationExecution.class);
        verify(executionStore).save(captor.capture());
        assertEquals(RuleGenerationStatus.SUCCEEDED, captor.getValue().getStatus());
        assertEquals("https://gitlab/mr/1", captor.getValue().getMrUrl());
    }

    @Test
    void shouldMarkExecutionFailedAndRethrow_whenGitLabTriggerFails() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        when(gitLabClient.triggerPipeline(any(), any(), any()))
                .thenThrow(new RuntimeException("GitLab unavailable"));

        final var request = TriggerRuleGenRequest.builder().targetPath("devx").build();
        final var thrown = assertThrows(RuntimeException.class,
                () -> service.triggerRuleGeneration(request));

        assertEquals("GitLab unavailable", thrown.getMessage());
        final var captor = ArgumentCaptor.forClass(RuleGenerationExecution.class);
        verify(executionStore, atLeast(2)).save(captor.capture());
        final var savedExecutions = captor.getAllValues();
        assertEquals(RuleGenerationStatus.REQUESTED, savedExecutions.get(0).getStatus());
        assertEquals(RuleGenerationStatus.FAILED, savedExecutions.get(1).getStatus());
        assertEquals("GitLab unavailable", savedExecutions.get(1).getMessage());
    }

    @Test
    void shouldAddSuppressedException_whenFailureUpdateAlsoFails() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        final var gitlabError = new RuntimeException("GitLab unavailable");
        when(gitLabClient.triggerPipeline(any(), any(), any())).thenThrow(gitlabError);
        final var storeError = new RuntimeException("ES unavailable");
        org.mockito.Mockito.doThrow(storeError)
                .when(executionStore).save(org.mockito.ArgumentMatchers.argThat(
                        e -> e.getStatus() == RuleGenerationStatus.FAILED));

        final var thrown = assertThrows(RuntimeException.class,
                () -> service.triggerRuleGeneration(
                        TriggerRuleGenRequest.builder().targetPath("devx").build()));

        assertEquals(1, thrown.getSuppressed().length);
        assertEquals(storeError, thrown.getSuppressed()[0]);
    }

    @Test
    void shouldMarkTriggeredAndReturnResult_onSuccessfulTrigger() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        when(gitLabClient.triggerPipeline(any(), any(), any())).thenReturn(77L);

        final var result = service.triggerRuleGeneration(
                TriggerRuleGenRequest.builder().targetPath("devx").build());

        assertEquals(77L, result.getPipelineId());
        assertNotNull(result.getExecutionId());
        final var captor = ArgumentCaptor.forClass(RuleGenerationExecution.class);
        verify(executionStore, atLeast(2)).save(captor.capture());
        final var saved = captor.getAllValues();
        assertEquals(RuleGenerationStatus.REQUESTED, saved.get(0).getStatus());
        assertEquals(RuleGenerationStatus.TRIGGERED, saved.get(1).getStatus());
        assertEquals(77L, saved.get(1).getPipelineId());
    }

    @Test
    void shouldStillReturnResult_whenMarkTriggeredFails() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        when(gitLabClient.triggerPipeline(any(), any(), any())).thenReturn(55L);
        org.mockito.Mockito.doThrow(new RuntimeException("ES unavailable"))
                .when(executionStore).save(org.mockito.ArgumentMatchers.argThat(
                        e -> e.getStatus() == RuleGenerationStatus.TRIGGERED));

        final var result = service.triggerRuleGeneration(
                TriggerRuleGenRequest.builder().targetPath("devx").build());

        assertEquals(55L, result.getPipelineId());
        assertNotNull(result.getExecutionId());
    }

    @Test
    void shouldThrowBadRequest_whenCompletionStatusIsNotTerminal() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.TRIGGERED)
                .build();

        assertThrows(ClientErrorException.class,
                () -> service.completeRuleGeneration("exec-1", request));
        verify(executionStore, never()).get(any());
    }

    @Test
    void shouldThrowNotFound_whenExecutionIdUnknown() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        when(executionStore.get("unknown")).thenReturn(Optional.empty());

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();

        assertThrows(NotFoundException.class,
                () -> service.completeRuleGeneration("unknown", request));
    }

    @Test
    void shouldReturnCurrent_whenAlreadyCompleteWithSameStatus() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        final var existing = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();
        when(executionStore.get("exec-1")).thenReturn(Optional.of(existing));

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();

        final var result = service.completeRuleGeneration("exec-1", request);
        assertEquals(existing, result);
        verify(executionStore, never()).save(any());
    }

    @Test
    void shouldThrowConflict_whenAlreadyCompleteWithDifferentStatus() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        final var existing = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();
        when(executionStore.get("exec-1")).thenReturn(Optional.of(existing));

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.FAILED)
                .build();

        assertThrows(ClientErrorException.class,
                () -> service.completeRuleGeneration("exec-1", request));
    }

    @Test
    void shouldCompleteWithSkippedStatus_andAllFields() {
        final var service = new PipelineServiceImpl(gitLabClient,
                PipelineConfig.builder().clioProjectId("123").build(), executionStore);
        final var existing = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .status(RuleGenerationStatus.TRIGGERED)
                .createdAt(1L)
                .updatedAt(1L)
                .build();
        when(executionStore.get("exec-1")).thenReturn(Optional.of(existing));

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.SKIPPED)
                .mrUrl("https://gitlab/mr/2")
                .rulesGenerated(5)
                .filesUpdated(java.util.List.of("a.txt"))
                .filesCreated(java.util.List.of("b.txt"))
                .message("done")
                .build();

        final var result = service.completeRuleGeneration("exec-1", request);

        assertEquals(RuleGenerationStatus.SKIPPED, result.getStatus());
        assertEquals("https://gitlab/mr/2", result.getMrUrl());
        assertEquals(5, result.getRulesGenerated());
        assertEquals(java.util.List.of("a.txt"), result.getFilesUpdated());
        assertEquals(java.util.List.of("b.txt"), result.getFilesCreated());
        assertEquals("done", result.getMessage());
    }
}
