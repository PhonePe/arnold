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

package com.phonepe.arnold.core.clients;

import lombok.experimental.UtilityClass;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

/**
 * Factory for OkHttp clients that honour the standard proxy environment variables.
 */
@UtilityClass
public class HttpClients {

    public OkHttpClient create(final HttpClientSettings settings) {
        return new OkHttpClient.Builder()
                .proxySelector(EnvironmentProxySelector.fromEnvironment(ProxySelector.getDefault()))
                .connectionPool(new ConnectionPool(settings.getConnections(), settings.getIdleTimeOutSeconds(), TimeUnit.SECONDS))
                .connectTimeout(settings.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(settings.getOpTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(settings.getOpTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    public OkHttpClient create(final long timeoutMs) {
        return create(HttpClientSettings.builder()
                .connectTimeoutMs((int) timeoutMs)
                .opTimeoutMs((int) timeoutMs)
                .build());
    }

    /**
     * @return the {@code rel="next"} target of an RFC 8288 {@code Link} header, or null when absent
     */
    public String nextLink(final String header) {
        if (header == null) {
            return null;
        }
        for (final var part : header.split(",")) {
            final var segments = part.split(";");
            for (var i = 1; i < segments.length; i++) {
                if ("rel=\"next\"".equals(segments[i].trim())) {
                    final var target = segments[0].trim();
                    return target.startsWith("<") && target.endsWith(">")
                            ? target.substring(1, target.length() - 1) : target;
                }
            }
        }
        return null;
    }

    public String trimTrailingSlash(final String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
