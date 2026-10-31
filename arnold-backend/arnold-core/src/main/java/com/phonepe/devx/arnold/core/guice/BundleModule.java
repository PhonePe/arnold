package com.phonepe.devx.arnold.core.guice;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;
import org.apache.curator.framework.CuratorFramework;

import java.util.function.Supplier;

@AllArgsConstructor
public class BundleModule<T extends Configuration> extends AbstractModule {

    private final PlatformBundle<T> platformBundle;
    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private final Supplier<ArnoldCoreConfiguration> bundleConfigSupplier;

    @Override
    protected void configure() {
        bind(CuratorFramework.class).toInstance(platformBundle.getCurator().get());
    }

    @Provides
    @Singleton
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    @Provides
    @Singleton
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Provides
    @Singleton
    public Supplier<ArnoldCoreConfiguration> getArnoldBundleConfigSupplier() {
        return bundleConfigSupplier;
    }
}
