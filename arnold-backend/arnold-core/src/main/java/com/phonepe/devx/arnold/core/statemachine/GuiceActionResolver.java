package com.phonepe.devx.arnold.core.statemachine;

import com.google.inject.Injector;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link ActionResolver} backed by the Guice {@link Injector}, so transition actions and guards can be
 * dependency-injected singletons (e.g. {@code InvokeAnalysisAction} reaching the
 * {@code AnalysisOrchestrator}). This is the production resolver; tests use the reflective default.
 */
@Singleton
public class GuiceActionResolver implements ActionResolver {

    private final Injector injector;

    @Inject
    public GuiceActionResolver(final Injector injector) {
        this.injector = injector;
    }

    @Override
    public <T> T resolve(final Class<T> type) {
        return injector.getInstance(type);
    }
}
