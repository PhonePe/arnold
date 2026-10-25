package com.phonepe.arnold.models.integration;

import javax.validation.constraints.NotBlank;

public record CopilotAttemptRequest(@NotBlank String attemptId) { }
