package com.phonepe.devx.arnold.server.events;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.commons.columbus.storage.StorageClient;
import com.phonepe.data.provider.core.DataProvider;
import com.phonepe.dataplatform.EventIngestorClient;
import com.phonepe.dataplatform.EventIngestorClientConfig;
import com.phonepe.platform.http.v2.discovery.ServiceEndpointProviderFactory;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.utils.ConfigUtils;
import com.phonepe.devx.arnold.server.ArnoldConfiguration;
import com.phonepe.devx.arnold.server.config.ElasticSearchConfiguration;
import io.dropwizard.setup.Environment;
import lombok.AllArgsConstructor;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.function.Supplier;

@AllArgsConstructor
public class ServerModule extends AbstractModule {

    private DataProvider<ArnoldConfiguration> configProvider;
    private ColumbusBundle<ArnoldConfiguration> columbusBundle;

    @Provides
    @Singleton
    public EventIngestorClient getEventIngestorClient(
            final @Config EventIngestorClientConfig eventIngestionConfig,
            final ServiceEndpointProviderFactory serviceEndpointProviderFactory,
            final Environment environment,
            final @OlympusAuthSupplier Supplier<String> authSupplier
    ) throws Exception {
        return new EventIngestorClient(
                eventIngestionConfig,
                serviceEndpointProviderFactory,
                environment.getObjectMapper(),
                environment.metrics(),
                authSupplier
        );
    }

    @Provides
    @Singleton
    @Named(BindingNames.EVENT_INGESTION)
    public Supplier<Boolean> getEventIngestionDisabled(final Supplier<ArnoldConfiguration> configuration) {
        return ConfigUtils.mappedSupplier(configuration, ArnoldConfiguration::isEventIngestionDisabled);
    }


    @Provides
    @Singleton
    Supplier<ArnoldConfiguration> getDataProvider(final ArnoldConfiguration initialConfiguration) {
        final var provider = configProvider;
        if (provider != null) {
            return provider::getData;
        }
        return () -> initialConfiguration;
    }

    @Provides
    @Singleton
    public ArnoldCoreConfiguration getServiceCoreConfiguration(final ArnoldConfiguration configuration) {
        return configuration.getServiceCoreConfiguration();
    }

    @Provides
    @Singleton
    public ElasticSearchConfiguration elasticSearchConfiguration(final ArnoldConfiguration configuration) {
        return configuration.getElasticSearchConfiguration();
    }

    @Provides
    @Singleton
    public Columbus getColumbus() {
        return columbusBundle.columbus();
    }

    @Provides
    @Singleton
    public StorageClient getStorageClient(Columbus columbus) {
        return columbus.storage("arnold-es-cluster");
    }
}
