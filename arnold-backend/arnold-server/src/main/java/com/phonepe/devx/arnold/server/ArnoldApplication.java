package com.phonepe.devx.arnold.server;

import com.google.common.base.Strings;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.commons.columbus.core.toolkit.ToolkitSettings;
import com.phonepe.commons.columbus.dropwizard.internal.initializer.DropwizardColumbusInitializer;
import com.phonepe.commons.columbus.internal.core.toolkit.DefaultToolkitInitializer;
import com.phonepe.commons.columbus.reflect.ReflectSettings;
import com.phonepe.commons.columbus.storage.settings.StorageSettings;
import com.phonepe.devx.arnold.server.aerospike.AerospikeModule;
import com.phonepe.devx.arnold.core.ArnoldBundle;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.utils.MapperUtils;
import com.phonepe.devx.arnold.server.events.ServerModule;
import com.phonepe.i18n.model.ValidationListener;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
import com.phonepe.platform.dw.bundle.core.PlatformBundleConfig;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.phonepe.spyglass.client.common.config.SpyGlassOptions;
import com.phonepe.spyglass.client.tracking.common.SpyglassTrackingModule;
import com.phonepe.spyglass.client.tracking.common.SpyglassTrackingModuleConfig;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import com.phonepe.devx.arnold.core.elastic.ManagedProjectionWorker;
import com.phonepe.devx.arnold.core.sync.ManagedUserSyncWorker;
import lombok.val;
import ru.vyarus.dropwizard.guice.GuiceBundle;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ArnoldApplication extends BaseApplication<ArnoldConfiguration> {

    public static final String APP_NAME = "arnold";
    private static final String TEAM_ID = "devx";
    @SuppressWarnings("java:S1450")
    private GuiceBundle guiceBundle;
    protected PlatformBundle<ArnoldConfiguration> platformBundle;

    public static void main(String[] args) throws Exception {
        new ArnoldApplication().run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    protected PlatformBundle<ArnoldConfiguration> createPlatformBundle() {
        return new PlatformBundle<>() {
            @Override
            protected PlatformBundleConfig getConfig(ArnoldConfiguration configuration) {
                return configuration.getPlatformBundle();
            }

            @Override
            protected Supplier<Injector> getInjector(ArnoldConfiguration configuration) {
                return () -> guiceBundle.getInjector();
            }

            @Override
            protected boolean withOor(ArnoldConfiguration configuration) {
                return false;
            }

            @Override
            protected boolean enableLogCorrelationFeature(ArnoldConfiguration configuration) {
                return false;
            }

            @Override
            protected Supplier<ValidationListener> getI18nValidationListener(ArnoldConfiguration configuration) {
                return () -> null;
            }

            @Override
            protected Supplier<SpyGlassOptions> refreshSpyglassOptions(ArnoldConfiguration configuration) {
                return () -> null;
            }

            @Override
            protected List<SpyglassTrackingModule<? extends SpyglassTrackingModuleConfig>> getSpyglassTrackingModules() {
                return List.of();
            }
        };
    }

    @Override
    public void initialize(final Bootstrap<ArnoldConfiguration> bootstrap) {

        final var roseyPath = getEnv("ROSEY_PATH", "/rosey/config.yml");
        final var configName = getEnv("ROSEY_CONFIG_NAME", APP_NAME);
        final var roseyTeamId = getEnv("ROSEY_TEAM_ID", TEAM_ID);

        final var configProviderBundle = appConfigProviderBundle(roseyPath, roseyTeamId, configName);

        boolean localConfig = Boolean.parseBoolean(System.getProperty("localConfig", "false"));
        if (!localConfig) {
            bootstrap.addBundle(configProviderBundle);
        }
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                localConfig ? bootstrap.getConfigurationSourceProvider()
                            : new RoseyConfigSourceProvider(roseyTeamId, configName),
                new EnvironmentVariableSubstitutor()));

        this.platformBundle = createPlatformBundle();
        bootstrap.addBundle(platformBundle);
        bootstrap.addBundle(hystrixBundle());
        final var columbusBundle = createColumbusBundle();
        bootstrap.addBundle(columbusBundle);
        final var serviceBundle = new ArnoldBundle<ArnoldConfiguration>() {

            @Override
            public Supplier<ArnoldCoreConfiguration> bundleConfigSupplier() {
                return () -> configProviderBundle.getDataProvider().getData().getServiceCoreConfiguration();
            }

            @Override
            public PlatformBundle<ArnoldConfiguration> platformBundle() {
                return platformBundle;
            }
        };
        this.guiceBundle = guiceBundle(
                Stream.concat(serviceBundle.packageNameList(), Stream.of(getClass().getPackage().getName()))
                        .toArray(String[]::new),
                Stream.concat(serviceBundle.modules(bootstrap),
                              Stream.of(new ServerModule(configProviderBundle.getDataProvider(), columbusBundle,
                                                         platformBundle),
                                      new AerospikeModule()))
                        .toArray(Module[]::new));
        bootstrap.addBundle(guiceBundle);
    }

    @Override
    public void run(final ArnoldConfiguration configuration, final Environment environment) {
        MapperUtils.configureMapper(environment.getObjectMapper());
        HystrixConfigurationFactory.init(configuration.getHystrixConfig());
        FunctionMetricsManager.initialize("commands", environment.metrics());
        environment.lifecycle().manage(guiceBundle.getInjector().getInstance(ManagedProjectionWorker.class));
        environment.lifecycle().manage(guiceBundle.getInjector().getInstance(ManagedUserSyncWorker.class));
    }

    private String getEnv(String name, String defaultValue) {
        final var value = System.getenv(name);
        return Strings.isNullOrEmpty(value) ? defaultValue : value;
    }

    ColumbusBundle<ArnoldConfiguration> createColumbusBundle() {
        val reflectSettings = ReflectSettings.builder()
                .packageName("com.phonepe.devx.arnold.server.elastic")
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
