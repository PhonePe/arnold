package com.phonepe.devx.arnold.core.config;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public record ClioPipelineConfiguration(
        @Positive long projectId,
        @NotBlank String ref,
        @NotBlank String triggerToken
) {
}
