package com.phonepe.devx.arnold.server;

import com.google.inject.Module;
import com.google.inject.Stage;
import io.dropwizard.Application;
import ru.vyarus.dropwizard.guice.GuiceBundle;

abstract class BaseApplication<T extends BaseConfiguration> extends Application<T> {

    GuiceBundle guiceBundle(String[] packages, Module... modules) {
        return GuiceBundle.builder()
                .enableAutoConfig(packages)
                .modules(modules)
                .build(Stage.PRODUCTION);
    }
}
