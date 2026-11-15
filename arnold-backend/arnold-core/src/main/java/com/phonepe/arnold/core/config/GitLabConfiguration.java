/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
