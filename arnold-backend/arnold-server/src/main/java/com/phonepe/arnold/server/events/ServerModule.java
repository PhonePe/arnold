package com.phonepe.arnold.server.events;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.server.ArnoldConfiguration;
import com.phonepe.arnold.server.config.ElasticSearchConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServerModule extends AbstractModule {

    private final ColumbusBundle<ArnoldConfiguration> columbusBundle;

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
