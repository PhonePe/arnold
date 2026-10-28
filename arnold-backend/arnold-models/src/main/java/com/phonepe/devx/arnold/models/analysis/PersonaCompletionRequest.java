package com.phonepe.devx.arnold.models.analysis;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.Valid;
import java.util.List;

@Value
@Builder
@Jacksonized
public class PersonaCompletionRequest {
    @NotBlank
    String mergeRequestId;
    @NotBlank
    String runId;
    @NotBlank
    String personaId;
    @NotNull
    Outcome outcome;
    String detail;
    @Valid
    @NotNull
    @Size(max = 500)
    @Builder.Default
    List<@NotNull AnalysisFindingResponse> findings = List.of();
    @Valid
    TokenUsage usage;

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
