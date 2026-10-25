package com.phonepe.arnold.core.guice;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import lombok.AllArgsConstructor;

import java.util.function.Supplier;

@AllArgsConstructor
public class BundleModule extends AbstractModule {

    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private final Supplier<ArnoldCoreConfiguration> bundleConfigSupplier;

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
