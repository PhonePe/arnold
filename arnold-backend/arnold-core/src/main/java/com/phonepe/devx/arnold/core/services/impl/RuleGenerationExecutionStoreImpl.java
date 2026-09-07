package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.services.RuleGenerationExecutionStore;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;

import java.util.Optional;

import static com.phonepe.devx.arnold.core.common.Constants.RULE_GENERATION_INDEX_REFERENCE;

@Singleton
public class RuleGenerationExecutionStoreImpl implements RuleGenerationExecutionStore {
    private static final TypeReference<RuleGenerationExecution> TYPE = new TypeReference<>() { };

    private final ESStorageManager storageManager;

    @Inject
    public RuleGenerationExecutionStoreImpl(final ESStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @Override
    public void save(final RuleGenerationExecution execution) {
        storageManager.save(RULE_GENERATION_INDEX_REFERENCE, execution.getExecutionId(), execution);
    }

    @Override
    public Optional<RuleGenerationExecution> get(final String executionId) {
        return storageManager.get(RULE_GENERATION_INDEX_REFERENCE, executionId, TYPE);
    }
}
