/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.Positive;

/**
 * GitHub App settings (github.com or GitHub Enterprise Server).
 *
 * <p>The app acts for signed-in users through user-to-server tokens obtained with the app's OAuth flow, and for
 * Arnold itself (runner credentials, background work) through installation tokens minted with its private key.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString(exclude = {"clientSecret", "privateKey", "webhookSecret"})
public class GitHubConfiguration {
    private boolean enabled;
    private String webUrl = "https://github.com";
    private String apiUrl = "https://api.github.com";
    /** Numeric GitHub App id. */
    private long appId;
    /** App slug, used for the installation link shown to users. */
    private String appSlug;
    private String clientId;
    private String clientSecret;
    /** App private key (PEM, PKCS#1 or PKCS#8). */
    private String privateKey;
    /** Arnold UI URL that receives the OAuth redirect, e.g. https://arnold.example.com/github/oauth/callback. */
    private String callbackUrl;
    private String webhookSecret;
    @Positive
    private long oauthStateTtlMs = 600_000;
    @Positive
    private long tokenRefreshBufferMs = 60_000;
    @Positive
    private long requestTimeoutMs = 30_000;

    public boolean configured() {
        return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
