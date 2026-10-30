package com.phonepe.devx.arnold.models.integration;

import javax.validation.constraints.NotBlank;

public record CopilotAttemptRequest(@NotBlank String attemptId) { }
