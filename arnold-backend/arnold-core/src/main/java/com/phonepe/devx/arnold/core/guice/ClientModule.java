package com.phonepe.devx.arnold.core.guice;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.dataplatform.interceptor.AuthTokenInterceptor;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
import com.phonepe.platform.http.v2.client.ClientFactory;
import com.phonepe.platform.http.v2.common.HttpConfiguration;
import com.phonepe.platform.http.v2.discovery.ServiceEndpointProviderFactory;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.clients.ClientHttpExecutorFactory;
import com.phonepe.devx.arnold.core.clients.HttpConfigurationHelper;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.zeus.models.routing.RoutingRequest;

import io.dropwizard.setup.Environment;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.reflections.Reflections;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class ClientModule<T extends Configuration> extends AbstractModule {

    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private final PlatformBundle<T> platformBundle;

    @Override
    protected void configure() {
        requestStaticInjection(ClientHttpExecutorFactory.class);
    }

    @Provides
    @Singleton
    public ServiceEndpointProviderFactory getEndpointFactory() {
        return platformBundle.getServiceEndpointProviderFactory();
    }

    @Provides
    @Singleton
    @Named(BindingNames.CLIENT_FACTORY)
    public Map<String, HttpExecutorBuilderFactory> getClientConfigs(
            final ServiceEndpointProviderFactory endpointProviderFactory,
            final HttpConfigurationHelper httpConfigurationHelper) {

        final var reflections = new Reflections("com.phonepe.devx.arnold.core.services");
        final var clientDeclarations = reflections.getTypesAnnotatedWith(ClientDeclaration.class);
        if (clientDeclarations.isEmpty()) {
            log.warn("No client declarations found. Returning empty client factory map.");
            return Map.of();
        }
        log.info("Found {} client declarations", clientDeclarations.size());

        return clientDeclarations.stream()
                .map(clientClass -> {
                    ClientDeclaration clientDeclaration = clientClass.getAnnotation(ClientDeclaration.class);
                    String clientName = clientDeclaration.value();
                    HttpConfiguration httpConfiguration = httpConfigurationHelper.fetchHttpConfiguration(clientName);
                    RoutingRequest routingRequest = httpConfigurationHelper.buildRoutingRequest(clientName);
                    HttpExecutorBuilderFactory factory = createHttpExecutorBuilderFactory(
                            endpointProviderFactory, httpConfiguration, routingRequest, clientClass);
                    return Map.entry(clientName, factory);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private HttpExecutorBuilderFactory createHttpExecutorBuilderFactory(
            final ServiceEndpointProviderFactory endpointProviderFactory,
            final HttpConfiguration httpConfiguration,
            final RoutingRequest routingRequest,
            final Class<?> callerClass
    ) {
        return HttpExecutorBuilderFactory.builder()
                .client(createHttpClient(httpConfiguration))
                .mapper(objectMapper)
                .routingRequest(routingRequest)
                .endpointProviderSupplier(() -> endpointProviderFactory.provider(httpConfiguration))
                .callerClass(callerClass)
                .build();
    }

    private OkHttpClient createHttpClient(final HttpConfiguration httpConfiguration) {
        final OkHttpClient client;
        try {
            client = ClientFactory.newHttpClientBuilder()
                    .withMetricRegistry(metricRegistry).withConfiguration(
                            httpConfiguration).withNetworkInterceptor(
                            new AuthTokenInterceptor(
                                    () -> platformBundle.getOlympusIMClient().getSystemAuthHeader())).build();
        } catch (final GeneralSecurityException | IOException e) {
            throw ArnoldError.propagate(e).build();
        }
        return client;
    }
}
