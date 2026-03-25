package com.phonepe.devx.arnold.core;

import com.google.inject.AbstractModule;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.platform.http.v2.discovery.HttpDiscoveryBundle;
import com.phonepe.devx.arnold.core.guice.BundleModule;
import com.phonepe.devx.arnold.core.guice.ClientModule;
import com.phonepe.devx.arnold.core.guice.ConfigModule;
import com.phonepe.devx.arnold.core.guice.CoreModule;
import com.phonepe.devx.arnold.core.guice.DBModule;
import com.phonepe.devx.arnold.core.utils.MapperUtils;
import io.appform.ranger.discovery.bundle.ServiceDiscoveryBundle;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class ArnoldBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Override
    public void run(T configuration, Environment environment) {
        MapperUtils.configureMapper(environment.getObjectMapper());
    }

    @Override
    public void initialize(Bootstrap bootstrap) {

    }

    public Stream<AbstractModule> modules(Bootstrap<T> bootstrap) {
        return Stream.of(
                new BundleModule<>(
                        serviceDiscoveryBundle(),
                        bootstrap.getObjectMapper(),
                        bootstrap.getMetricRegistry(),
                        bundleConfigSupplier()),
                new DBModule(),
                new ClientModule<>(
                        bootstrap.getObjectMapper(),
                        bootstrap.getMetricRegistry(),
                        olympusIMBundle(),
                        discoveryBundle()),
                new CoreModule<>(olympusIMBundle()),
                new ConfigModule()
        );
    }

    public Stream<String> packageNameList() {
        return Stream.of("com.phonepe.devx.arnold.core");
    }

    public abstract Supplier<ArnoldCoreConfiguration> bundleConfigSupplier();

    public abstract HttpDiscoveryBundle<T> discoveryBundle();

    public abstract ServiceDiscoveryBundle<T> serviceDiscoveryBundle();

    public abstract OlympusIMBundle<T> olympusIMBundle();

}
