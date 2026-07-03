package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.AsyncExecutorConfig;

import java.util.function.Supplier;

public class ConfigModule extends AbstractModule {

    @Provides
    @Singleton
    public AsyncExecutorConfig asyncExecutorConfig(final Supplier<ArnoldCoreConfiguration> coreConfigSupplier) {
        final var asyncExecutor = coreConfigSupplier.get().getAsyncExecutor();
        // Fall back to defaults if the block is absent from config so consumers always get a non-null bean.
        return asyncExecutor != null ? asyncExecutor : AsyncExecutorConfig.builder().build();
    }
}
