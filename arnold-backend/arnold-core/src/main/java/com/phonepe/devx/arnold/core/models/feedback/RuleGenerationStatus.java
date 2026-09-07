package com.phonepe.devx.arnold.core.models.feedback;

public enum RuleGenerationStatus {
    REQUESTED,
    TRIGGERED,
    SUCCEEDED,
    SKIPPED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == SKIPPED || this == FAILED;
    }
}
