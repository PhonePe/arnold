package com.phonepe.devx.arnold.core.config;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public record GitLabConfiguration(
        @NotBlank String baseUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String callbackUrl,
        @Positive long oauthStateTtlMs,
        @Positive long tokenRefreshBufferMs,
        @Positive long requestTimeoutMs
) {
}
