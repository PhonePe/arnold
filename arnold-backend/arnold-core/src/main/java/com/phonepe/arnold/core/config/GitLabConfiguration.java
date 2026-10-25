package com.phonepe.arnold.core.config;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.Valid;

/**
 * GitLab instance settings.
 *
 * @param serviceToken optional token (personal/project/group access token with {@code read_api}) used for
 *                     server-side lookups that are not tied to a signed-in user, such as review feedback
 *                     enrichment. Enrichment is skipped when absent.
 */
public record GitLabConfiguration(
        @NotBlank String baseUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String callbackUrl,
        @Positive long oauthStateTtlMs,
        @Positive long tokenRefreshBufferMs,
        @Positive long requestTimeoutMs,
        @Valid ClioPipelineConfiguration clio,
        String serviceToken
) {
    public GitLabConfiguration(final String baseUrl, final String clientId, final String clientSecret,
                               final String callbackUrl, final long oauthStateTtlMs,
                               final long tokenRefreshBufferMs, final long requestTimeoutMs,
                               final ClioPipelineConfiguration clio) {
        this(baseUrl, clientId, clientSecret, callbackUrl, oauthStateTtlMs, tokenRefreshBufferMs, requestTimeoutMs,
                clio, null);
    }

    public GitLabConfiguration(final String baseUrl, final String clientId, final String clientSecret,
                               final String callbackUrl, final long oauthStateTtlMs,
                               final long tokenRefreshBufferMs, final long requestTimeoutMs) {
        this(baseUrl, clientId, clientSecret, callbackUrl, oauthStateTtlMs, tokenRefreshBufferMs, requestTimeoutMs,
                null, null);
    }

    @Override
    public String toString() {
        return "GitLabConfiguration[baseUrl=" + baseUrl + ", clientId=" + clientId + ", callbackUrl=" + callbackUrl + "]";
    }
}
