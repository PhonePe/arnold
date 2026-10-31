package com.phonepe.devx.arnold.core.config;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.Valid;

public record GitLabConfiguration(
        @NotBlank String baseUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String callbackUrl,
        @Positive long oauthStateTtlMs,
        @Positive long tokenRefreshBufferMs,
        @Positive long requestTimeoutMs,
        @Valid ClioPipelineConfiguration clio
) {
    public GitLabConfiguration(final String baseUrl, final String clientId, final String clientSecret,
                               final String callbackUrl, final long oauthStateTtlMs,
                               final long tokenRefreshBufferMs, final long requestTimeoutMs) {
        this(baseUrl, clientId, clientSecret, callbackUrl, oauthStateTtlMs, tokenRefreshBufferMs, requestTimeoutMs,
                null);
    }
}
