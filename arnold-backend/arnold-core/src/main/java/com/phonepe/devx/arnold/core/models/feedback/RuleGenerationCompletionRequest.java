package com.phonepe.devx.arnold.core.models.feedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleGenerationCompletionRequest {
    @NotNull
    RuleGenerationStatus status;
    String mrUrl;
    RuleGenerationMrAction mrAction;
    @Min(0)
    Integer rulesGenerated;
    @Builder.Default
    List<String> filesUpdated = List.of();
    @Builder.Default
    List<String> filesCreated = List.of();
    @Size(max = 2000)
    String message;
}
