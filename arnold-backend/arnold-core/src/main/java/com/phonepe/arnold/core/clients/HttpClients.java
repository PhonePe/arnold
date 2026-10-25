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
                .connectionPool(new ConnectionPool(settings.getConnections(), settings.getIdleTimeOutSeconds(),
                        TimeUnit.SECONDS))
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

    public String trimTrailingSlash(final String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
