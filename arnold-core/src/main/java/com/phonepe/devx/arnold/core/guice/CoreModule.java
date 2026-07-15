package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.impl.CibotServiceHttpImpl;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@AllArgsConstructor
public class CoreModule<T extends Configuration> extends AbstractModule {
    private final OlympusIMBundle<T> olympusIMBundle;

    @Override
    protected void configure() {
        bind(CibotService.class).to(CibotServiceHttpImpl.class);
    }

    @Provides
    @Singleton
    @OlympusAuthSupplier
    public Supplier<String> olympusAuthSupplier() {
        return () -> olympusIMBundle.getOlympusIMClient().getSystemAuthHeader();
    }

    @Provides
    @Singleton
    public OlympusIMClient olympusIMClient() {
        return olympusIMBundle.getOlympusIMClient();
    }

    @Provides
    @Singleton
    @Named(BindingNames.ASYNC_TASK_EXECUTOR)
    public ExecutorService asyncTaskExecutor() {
        return Executors.newCachedThreadPool();
    }
}

