package com.phonepe.devx.arnold.core;

import com.google.inject.AbstractModule;
import com.phonepe.devx.arnold.core.guice.BundleModule;
import com.phonepe.devx.arnold.core.guice.ConfigModule;
import com.phonepe.devx.arnold.core.guice.CoreModule;
import com.phonepe.devx.arnold.core.guice.DBModule;
import com.phonepe.devx.arnold.core.utils.MapperUtils;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
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
                        platformBundle(),
                        bootstrap.getObjectMapper(),
                        bootstrap.getMetricRegistry(),
                        bundleConfigSupplier()),
                new DBModule(),
                new CoreModule<>(platformBundle()),
                new ConfigModule()
        );
    }

    public Stream<String> packageNameList() {
        return Stream.of("com.phonepe.devx.arnold.core");
    }

    public abstract Supplier<ArnoldCoreConfiguration> bundleConfigSupplier();

    public abstract PlatformBundle<T> platformBundle();

}
