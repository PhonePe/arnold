package com.phonepe.devx.arnold.core.guice;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import io.appform.ranger.discovery.bundle.ServiceDiscoveryBundle;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;
import org.apache.curator.framework.CuratorFramework;

import java.util.function.Supplier;

@AllArgsConstructor
public class BundleModule<T extends Configuration> extends AbstractModule {

    private final ServiceDiscoveryBundle<T> serviceDiscoveryBundle;
    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private final Supplier<ArnoldCoreConfiguration> bundleConfigSupplier;

    @Override
    protected void configure() {
        bind(CuratorFramework.class).toInstance(serviceDiscoveryBundle.getCurator());
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
