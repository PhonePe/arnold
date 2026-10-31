package com.phonepe.devx.arnold.models.integration;

public record CopilotExecutionTokenResponse(String accessToken, Long expiresAt) {
    @Override
    public String toString() {
        return "CopilotExecutionTokenResponse[redacted]";
    }
}
