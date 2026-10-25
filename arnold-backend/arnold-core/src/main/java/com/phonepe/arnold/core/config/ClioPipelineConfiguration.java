package com.phonepe.arnold.core.config;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public record ClioPipelineConfiguration(
        @Positive long projectId,
        @NotBlank String projectPath,
        @NotBlank String ref,
        @NotBlank String triggerToken
) {
    public ClioPipelineConfiguration(final long projectId, final String ref, final String triggerToken) {
        this(projectId, null, ref, triggerToken);
    }
}
