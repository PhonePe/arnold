package com.phonepe.devx.arnold.models.analysis;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
    // placeholder, this need to be worked on
    String detail;

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
