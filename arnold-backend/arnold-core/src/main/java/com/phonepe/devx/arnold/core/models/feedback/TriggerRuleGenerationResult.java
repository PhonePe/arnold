package com.phonepe.devx.arnold.core.models.feedback;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TriggerRuleGenerationResult {
    String executionId;
    long pipelineId;
}
