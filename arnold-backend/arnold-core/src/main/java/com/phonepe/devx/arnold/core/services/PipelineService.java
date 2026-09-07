package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationCompletionRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenerationResult;

/**
 * Orchestrates CI pipeline triggers for rule generation workflows.
 */
public interface PipelineService {

    /**
     * Trigger the rules-from-feedback pipeline in Clio.
     *
     * @param request rule generation request
     * @return execution and triggered pipeline ids
     */
    TriggerRuleGenerationResult triggerRuleGeneration(TriggerRuleGenRequest request);

    RuleGenerationExecution completeRuleGeneration(String executionId,
                                                   RuleGenerationCompletionRequest request);
}
