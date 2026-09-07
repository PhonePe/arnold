package com.phonepe.devx.arnold.core.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.PipelineConfig;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationCompletionRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationStatus;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenRequest;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenerationResult;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.phonepe.devx.arnold.core.services.PipelineService;
import com.phonepe.devx.arnold.core.services.RuleGenerationExecutionStore;
import com.phonepe.devx.arnold.models.request.TargetScope;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

@Slf4j
@Singleton
public class PipelineServiceImpl implements PipelineService {

    private final String clioProjectId;
    private final String clioPipelineRef;
    private final PipelineTriggerClient gitLabClient;
    private final RuleGenerationExecutionStore executionStore;

    @Inject
    public PipelineServiceImpl(final PipelineTriggerClient gitLabClient,
                               final PipelineConfig pipelineConfig,
                               final RuleGenerationExecutionStore executionStore) {
        this.gitLabClient = gitLabClient;
        this.executionStore = executionStore;
        this.clioProjectId = Objects.requireNonNull(
                pipelineConfig.getClioProjectId(),
                "pipelineConfig.clioProjectId must be set");
        if (clioProjectId.isBlank()) {
            throw new IllegalArgumentException("pipelineConfig.clioProjectId must not be blank");
        }
        this.clioPipelineRef = Objects.requireNonNull(
                pipelineConfig.getClioPipelineRef(),
                "pipelineConfig.clioPipelineRef must be set");
        if (clioPipelineRef.isBlank()) {
            throw new IllegalArgumentException("pipelineConfig.clioPipelineRef must not be blank");
        }
    }

    @Override
    public TriggerRuleGenerationResult triggerRuleGeneration(final TriggerRuleGenRequest request) {
        final var effectiveScope = request.getScope() != null ? request.getScope() : TargetScope.GROUP;
        final String executionId = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();
        final var execution = RuleGenerationExecution.builder()
                .executionId(executionId)
                .targetPath(request.getTargetPath())
                .scope(effectiveScope)
                .daysBack(request.getDaysBack())
                .minScore(request.getMinScore())
                .status(RuleGenerationStatus.REQUESTED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        executionStore.save(execution);

        log.info("Triggering rule generation: executionId={}, scope={}, targetPath={}, daysBack={}, minScore={}",
                executionId, effectiveScope, request.getTargetPath(), request.getDaysBack(), request.getMinScore());

        try {
            final long pipelineId = gitLabClient.triggerPipeline(
                    clioProjectId,
                    clioPipelineRef,
                    Map.of(
                            "WORKFLOW", "rules_from_feedback",
                            "TARGET_PATH", request.getTargetPath(),
                            "SCOPE", effectiveScope.name(),
                            "DAYS_BACK", String.valueOf(request.getDaysBack()),
                            "MIN_SCORE", String.valueOf(request.getMinScore()),
                            "ARNOLD_EXECUTION_ID", executionId
                    )
            );
            markTriggered(execution, pipelineId, executionId);
            return TriggerRuleGenerationResult.builder()
                    .executionId(executionId)
                    .pipelineId(pipelineId)
                    .build();
        } catch (RuntimeException e) {
            try {
                executionStore.save(execution.toBuilder()
                        .status(RuleGenerationStatus.FAILED)
                        .message(truncate(e.getMessage()))
                        .updatedAt(System.currentTimeMillis())
                        .build());
            } catch (RuntimeException updateError) {
                e.addSuppressed(updateError);
            }
            throw e;
        }
    }

    @Override
    public RuleGenerationExecution completeRuleGeneration(final String executionId,
            final RuleGenerationCompletionRequest request) {
        if (!request.getStatus().isTerminal()) {
            throw new ClientErrorException("Completion status must be terminal", Response.Status.BAD_REQUEST);
        }
        final var current = executionStore.get(executionId)
                .orElseThrow(() -> new NotFoundException("Unknown rule-generation execution: " + executionId));
        if (current.getStatus().isTerminal()) {
            if (current.getStatus() == request.getStatus()) {
                return current;
            }
            throw new ClientErrorException("Rule-generation execution is already complete",
                    Response.Status.CONFLICT);
        }
        final var completed = current.toBuilder()
                .status(request.getStatus())
                .mrUrl(request.getMrUrl())
                .mrAction(request.getMrAction())
                .rulesGenerated(request.getRulesGenerated())
                .filesUpdated(request.getFilesUpdated())
                .filesCreated(request.getFilesCreated())
                .message(request.getMessage())
                .updatedAt(System.currentTimeMillis())
                .build();
        executionStore.save(completed);
        return completed;
    }

    private void markTriggered(final RuleGenerationExecution execution, final long pipelineId,
            final String executionId) {
        try {
            executionStore.save(execution.toBuilder()
                    .pipelineId(pipelineId)
                    .status(RuleGenerationStatus.TRIGGERED)
                    .updatedAt(System.currentTimeMillis())
                    .build());
        } catch (RuntimeException e) {
            log.error("Pipeline {} triggered but execution {} could not be marked TRIGGERED",
                    pipelineId, executionId, e);
        }
    }

    private static String truncate(final String message) {
        if (message == null || message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }
}
