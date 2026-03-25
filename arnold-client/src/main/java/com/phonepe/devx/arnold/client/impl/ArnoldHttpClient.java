package com.phonepe.devx.arnold.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.devx.arnold.client.ArnoldClient;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

@AllArgsConstructor
public class ArnoldHttpClient implements ArnoldClient {
    private final HttpExecutorBuilderFactory executorFactory;

    @Override
    @SneakyThrows
    public ArnoldResponse<String> ping(final String prefix, @Nullable final String message) {
        final var builder = UriBuilder.fromPath("/housekeeping/v1/ping/"+ prefix);
        if (message != null) {
            builder.queryParam("message", message);
        }

        return executorFactory.<ArnoldResponse<String>>httpGetExecutorBuilder()
                .responseTypeReference(new TypeReference<>() {})
                .url(builder.build().toString())
                .command("ping")
                .build()
                .executeTracked();
    }
}
