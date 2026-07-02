package com.phonepe.devx.arnold.core.guice;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import com.phonepe.devx.arnold.core.AsyncExecutorConfig;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.SampleService;
import com.phonepe.devx.arnold.core.services.impl.CibotServiceHttpImpl;
import com.phonepe.devx.arnold.core.services.impl.SampleServiceHttpImpl;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CoreModule<T extends Configuration> extends AbstractModule {
    private final OlympusIMBundle<T> olympusIMBundle;

    @Override
    protected void configure() {
        bind(SampleService.class).to(SampleServiceHttpImpl.class);
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
    public ExecutorService asyncTaskExecutor(final AsyncExecutorConfig config) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("arnold-async-%d")
                .setDaemon(true)
                .build();
        // Bounded pool + CallerRunsPolicy: fan-out is throttled to maxSize so it can never
        // overflow the downstream Hystrix getMergeRequestNote pool (avoids RejectedExecutionException).
        // When saturated, the submitting thread runs the task inline as backpressure.
        final var executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

