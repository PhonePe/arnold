package com.phonepe.devx.arnold.server;

import com.google.inject.Module;
import com.google.inject.Stage;
import com.phonepe.data.provider.rosey.bundle.RoseyConfigProviderBundle;
import io.dropwizard.Application;
import org.zapodot.hystrix.bundle.HystrixBundle;
import ru.vyarus.dropwizard.guice.GuiceBundle;

abstract class BaseApplication<T extends BaseConfiguration> extends Application<T> {

    HystrixBundle<T> hystrixBundle() {
        // noinspection unchecked
        return HystrixBundle.builder()
                .disableStreamServletInAdminContext()
                .withApplicationStreamPath("/hystrix.stream")
                .build();
    }

    GuiceBundle guiceBundle(String[] packages, Module... modules) {
        return GuiceBundle.builder()
                .enableAutoConfig(packages)
                .modules(modules)
                .build(Stage.PRODUCTION);
    }

    RoseyConfigProviderBundle<T> appConfigProviderBundle(
            final String roseyPath,
            final String teamId,
            final String configName
    ) {
        return new RoseyConfigProviderBundle<>() {
            @Override
            public String getRoseyConfigPath(T configuration) {
                return roseyPath;
            }

            @Override
            public String getRoseyTeamId(T configuration) {
                return teamId;
            }

            @Override
            public String getRoseyConfigName(T configuration) {
                return configName;
            }
        };
    }
}
