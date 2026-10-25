package com.phonepe.arnold.core;

import com.google.inject.AbstractModule;
import com.phonepe.arnold.core.guice.BundleModule;
import com.phonepe.arnold.core.guice.ConfigModule;
import com.phonepe.arnold.core.guice.CoreModule;
import com.phonepe.arnold.core.guice.DBModule;
import com.phonepe.arnold.core.utils.MapperUtils;
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
                new BundleModule(
                        bootstrap.getObjectMapper(),
                        bootstrap.getMetricRegistry(),
                        bundleConfigSupplier()),
                new DBModule(),
                new CoreModule(),
                new ConfigModule()
        );
    }

    public Stream<String> packageNameList() {
        return Stream.of("com.phonepe.arnold.core");
    }

    public abstract Supplier<ArnoldCoreConfiguration> bundleConfigSupplier();

}
