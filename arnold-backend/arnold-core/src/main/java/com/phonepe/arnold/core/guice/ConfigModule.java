package com.phonepe.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.AsyncExecutorConfig;
import com.phonepe.arnold.core.auth.AuthConfiguration;
import com.phonepe.arnold.core.config.OpenCodeConfiguration;

public class ConfigModule extends AbstractModule {

    @Provides
    @Singleton
    public AsyncExecutorConfig asyncExecutorConfig(final ArnoldCoreConfiguration coreConfiguration) {
        final var asyncExecutor = coreConfiguration.getAsyncExecutor();
        // Fall back to defaults if the block is absent from config so consumers always get a non-null bean.
        return asyncExecutor != null ? asyncExecutor : AsyncExecutorConfig.builder().build();
    }


    @Provides
    @Singleton
    public AuthConfiguration authConfiguration(final ArnoldCoreConfiguration coreConfiguration) {
        return coreConfiguration.getAuth();
    }

    @Provides
    @Singleton
    public OpenCodeConfiguration openCodeConfiguration(final ArnoldCoreConfiguration coreConfiguration) {
        return coreConfiguration.getOpenCodeConfiguration();
    }
}
