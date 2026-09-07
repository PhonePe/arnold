package com.phonepe.devx.arnold.core.models.feedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.arnold.models.request.TargetScope;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleGenerationExecution {
    String executionId;
    String targetPath;
    TargetScope scope;
    int daysBack;
    double minScore;
    Long pipelineId;
    RuleGenerationStatus status;
    String mrUrl;
    RuleGenerationMrAction mrAction;
    Integer rulesGenerated;
    @Builder.Default
    List<String> filesUpdated = List.of();
    @Builder.Default
    List<String> filesCreated = List.of();
    String message;
    long createdAt;
    long updatedAt;
}
