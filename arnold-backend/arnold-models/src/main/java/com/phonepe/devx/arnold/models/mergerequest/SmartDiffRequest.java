package com.phonepe.devx.arnold.models.mergerequest;

import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Value
@Builder
@Jacksonized
public class SmartDiffRequest {
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{40,64}")
    String headSha;

    boolean regenerate;
    @javax.validation.Valid
    ExecutionOptions execution;
}
