package com.phonepe.arnold.server;

import com.google.inject.Module;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.commons.columbus.core.toolkit.ToolkitSettings;
import com.phonepe.commons.columbus.dropwizard.internal.initializer.DropwizardColumbusInitializer;
import com.phonepe.commons.columbus.internal.core.toolkit.DefaultToolkitInitializer;
import com.phonepe.commons.columbus.reflect.ReflectSettings;
import com.phonepe.commons.columbus.storage.settings.StorageSettings;
import com.phonepe.arnold.core.ArnoldBundle;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.utils.MapperUtils;
import com.phonepe.arnold.server.aerospike.AerospikeModule;
import com.phonepe.arnold.server.events.ServerModule;
import in.vectorpro.dropwizard.swagger.SwaggerBundle;
import in.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.val;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ArnoldApplication extends BaseApplication<ArnoldConfiguration> {

    public static final String APP_NAME = "arnold";

    private final AtomicReference<ArnoldConfiguration> configuration = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        new ArnoldApplication().run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    @Override
    public void initialize(final Bootstrap<ArnoldConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));

        bootstrap.addBundle(new ConfiguredBundle<>() {
            @Override
            public void run(final ArnoldConfiguration config, final Environment environment) {
                configuration.set(config);
            }
        });
        bootstrap.addBundle(new SwaggerBundle<>() {
            @Override
            protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(final ArnoldConfiguration config) {
                return config.getSwagger();
            }
        });
        final var columbusBundle = createColumbusBundle();
        bootstrap.addBundle(columbusBundle);
        final var serviceBundle = new ArnoldBundle<ArnoldConfiguration>() {
            @Override
            public Supplier<ArnoldCoreConfiguration> bundleConfigSupplier() {
                return () -> configuration.get().getServiceCoreConfiguration();
            }
        };
        final var guiceBundle = guiceBundle(
                Stream.concat(serviceBundle.packageNameList(), Stream.of(getClass().getPackage().getName()))
                        .toArray(String[]::new),
                Stream.concat(serviceBundle.modules(bootstrap),
                              Stream.of(new ServerModule(columbusBundle), new AerospikeModule()))
                        .toArray(Module[]::new));
        bootstrap.addBundle(guiceBundle);
    }

    @Override
    public void run(final ArnoldConfiguration configuration, final Environment environment) {
        MapperUtils.configureMapper(environment.getObjectMapper());
        FunctionMetricsManager.initialize("commands", environment.metrics());
    }

    ColumbusBundle<ArnoldConfiguration> createColumbusBundle() {
        val reflectSettings = ReflectSettings.builder()
                .packageName("com.phonepe.arnold.server.elastic")
                .build();

        val toolkitSettings = ToolkitSettings.builder()
                .reflectSettings(reflectSettings)
                .build();

        return ColumbusBundle.configure()
                .columbusInitializer(new DropwizardColumbusInitializer())
                .toolkitInitializer(new DefaultToolkitInitializer())
                .storageSettings(StorageSettings.defaults())
                .toolkitSettings(toolkitSettings)
                .build()
                .toBundle(config -> config.getElasticSearchConfiguration().getColumbus());
    }
}
