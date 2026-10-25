package com.phonepe.devx.arnold.server.events;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.data.provider.core.DataProvider;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.utils.ConfigUtils;
import com.phonepe.devx.arnold.server.ArnoldConfiguration;
import com.phonepe.devx.arnold.server.config.ElasticSearchConfiguration;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
import lombok.AllArgsConstructor;

import java.util.function.Supplier;

@AllArgsConstructor
public class ServerModule extends AbstractModule {

    private DataProvider<ArnoldConfiguration> configProvider;
    private ColumbusBundle<ArnoldConfiguration> columbusBundle;
    private PlatformBundle<ArnoldConfiguration> platformBundle;

    @Override
    protected void configure() {
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

}
