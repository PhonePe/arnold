package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;

import java.util.Optional;

public interface RuleGenerationExecutionStore {
    void save(RuleGenerationExecution execution);

    Optional<RuleGenerationExecution> get(String executionId);
}
