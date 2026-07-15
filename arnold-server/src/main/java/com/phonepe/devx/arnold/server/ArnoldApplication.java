package com.phonepe.devx.arnold.server;

import com.google.common.base.Strings;
import com.google.inject.Module;
import com.hystrix.configurator.core.HystrixConfigurationFactory;
import com.phonepe.commons.columbus.ColumbusBundle;
import com.phonepe.commons.columbus.core.toolkit.ToolkitSettings;
import com.phonepe.commons.columbus.dropwizard.internal.initializer.DropwizardColumbusInitializer;
import com.phonepe.commons.columbus.internal.core.toolkit.DefaultToolkitInitializer;
import com.phonepe.commons.columbus.reflect.ReflectSettings;
import com.phonepe.commons.columbus.storage.settings.StorageSettings;
import com.phonepe.devx.arnold.server.aerospike.AerospikeModule;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.platform.http.server.metrics.bundle.MetricBundle;
import com.phonepe.devx.arnold.server.events.ServerModule;
import com.phonepe.devx.arnold.core.utils.MapperUtils;
import com.phonepe.platform.http.v2.discovery.HttpDiscoveryBundle;
import com.phonepe.rosey.dwconfig.RoseyConfigSourceProvider;
import com.phonepe.devx.arnold.core.ArnoldBundle;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import io.appform.ranger.discovery.bundle.ServiceDiscoveryBundle;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.val;
import ru.vyarus.dropwizard.guice.GuiceBundle;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class ArnoldApplication extends BaseApplication<ArnoldConfiguration> {

    public static final String APP_NAME = "arnold";
    private static final String TEAM_ID = "devx";
    private GuiceBundle guiceBundle;

    public static void main(String[] args) throws Exception {
        new ArnoldApplication().run(args);
    }

    @Override
    public String getName() {
        return APP_NAME;
    }

    @Override
    public void initialize(final Bootstrap<ArnoldConfiguration> bootstrap) {

        final var serviceName = getEnv("SERVICE_NAME", APP_NAME);
        final var serviceDiscoveryBundle = serviceDiscoveryBundle(serviceName);

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
        bootstrap.addBundle(new MetricBundle<>());
        bootstrap.addBundle(swaggerBundle());
        bootstrap.addBundle(oorBundle());
        bootstrap.addBundle(hystrixBundle());
        bootstrap.addBundle(serviceDiscoveryBundle);
        final var olympusIMBundle = olympusIMBundle(serviceDiscoveryBundle, () -> this.guiceBundle.getInjector());
        bootstrap.addBundle(olympusIMBundle);
        HttpDiscoveryBundle<ArnoldConfiguration> httpDiscoveryBundle = httpDiscoveryBundle();
        bootstrap.addBundle(httpDiscoveryBundle);
        bootstrap.addBundle(requestInfoBundle(httpDiscoveryBundle));
        bootstrap.addBundle(metricIngestionBundle(httpDiscoveryBundle, olympusIMBundle));
        final var columbusBundle = createColumbusBundle();
        bootstrap.addBundle(columbusBundle);
        final var serviceBundle = new ArnoldBundle<ArnoldConfiguration>() {

            @Override
            public Supplier<ArnoldCoreConfiguration> bundleConfigSupplier() {
                return () -> configProviderBundle.getDataProvider().getData().getServiceCoreConfiguration();
            }

            @Override
            public HttpDiscoveryBundle<ArnoldConfiguration> discoveryBundle() {
                return httpDiscoveryBundle;
            }

            @Override
            public ServiceDiscoveryBundle<ArnoldConfiguration> serviceDiscoveryBundle() {
                return serviceDiscoveryBundle;
            }

            @Override
            public OlympusIMBundle<ArnoldConfiguration> olympusIMBundle() {
                return olympusIMBundle;
            }
        };
        this.guiceBundle = guiceBundle(
                Stream.concat(serviceBundle.packageNameList(), Stream.of(getClass().getPackage().getName()))
                        .toArray(String[]::new),
                Stream.concat(serviceBundle.modules(bootstrap),
                              Stream.of(new ServerModule(configProviderBundle.getDataProvider(), columbusBundle), new AerospikeModule()))
                        .toArray(Module[]::new));
        bootstrap.addBundle(guiceBundle);
        bootstrap.addBundle(validationBundle());
    }

    @Override
    public void run(final ArnoldConfiguration configuration, final Environment environment) {
        MapperUtils.configureMapper(environment.getObjectMapper());
        HystrixConfigurationFactory.init(configuration.getHystrixConfig());
        FunctionMetricsManager.initialize("commands", environment.metrics());
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
