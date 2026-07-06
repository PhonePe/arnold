package com.phonepe.devx.arnold.core.guice;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import com.phonepe.devx.arnold.core.AsyncExecutorConfig;
import com.phonepe.devx.arnold.core.async.LoggingCallerRunsPolicy;
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
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    public ExecutorService asyncTaskExecutor(final AsyncExecutorConfig config, final Environment environment) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("arnold-async-%d")
                .setDaemon(true)
                .build();
        // Bounded pool + caller-runs backpressure: fan-out is throttled to maxSize so it can never
        // overflow the downstream Hystrix getMergeRequestNote pool (avoids RejectedExecutionException).
        // When saturated, the submitting thread runs the task inline as backpressure -- see
        // LoggingCallerRunsPolicy, which additionally logs the (rare) saturation event.
        final var executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new LoggingCallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);

        // Tie the pool to the Dropwizard lifecycle so in-flight/queued tasks get a bounded grace
        // period to drain on deploy/SIGTERM instead of being abandoned when the daemon threads die.
        final long awaitSeconds = config.getShutdownAwaitSeconds();
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() {
                // The pool is ready as soon as it is constructed; nothing to start.
            }

            @Override
            public void stop() throws InterruptedException {
                log.info("Shutting down async task executor; awaiting up to {}s for in-flight tasks to drain",
                        awaitSeconds);
                executor.shutdown();
                if (executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                    log.info("Async task executor drained and shut down cleanly");
                    return;
                }
                final var dropped = executor.shutdownNow();
                log.warn("Async task executor did not drain within {}s; forced shutdown abandoned {} queued task(s)",
                        awaitSeconds, dropped.size());
            }
        });

        return executor;
    }
}

