package com.phonepe.devx.arnold.client;

import com.google.common.base.Preconditions;
import com.phonepe.platform.http.v2.client.ClientFactory;
import com.phonepe.platform.http.v2.common.HttpConfiguration;
import com.phonepe.platform.http.v2.common.ServiceEndpointProvider;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.devx.arnold.client.impl.ArnoldHttpClient;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.validation.constraints.NotNull;
import java.util.function.Supplier;

@UtilityClass
public class ArnoldClientBuilder {
    @SneakyThrows
    public static ArnoldClient build(
            @NotNull final HttpConfiguration config,
            final Supplier<ServiceEndpointProvider> endpointProviderSupplier,
            final Environment environment
    ) {
        Preconditions.checkArgument(config != null, "config cannot be null");

        final var client = ClientFactory.newHttpClientBuilder()
                .withMetricRegistry(environment.metrics())
                .withConfiguration(config)
                .build();

        final var executorBuilderFactory = HttpExecutorBuilderFactory.builder()
                .client(client)
                .endpointProviderSupplier(endpointProviderSupplier)
                .mapper(environment.getObjectMapper())
                .callerClass(ArnoldClient.class)
                .build();

        return new ArnoldHttpClient(executorBuilderFactory);
    }
}
