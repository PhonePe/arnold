package com.phonepe.arnold.core.elastic;

import com.phonepe.arnold.core.services.ProjectionService;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class ManagedProjectionWorker implements Managed {
    private final ProjectionService projectionService;
    private ScheduledExecutorService executor;

    @Inject
    public ManagedProjectionWorker(final ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final var thread = new Thread(runnable, "arnold-es-projection");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::processPending, 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void processPending() {
        try {
            log.info("Starting projection polling ...");
            projectionService.processPending(100);
            log.info("Projection polling cycle completed");
        } catch (RuntimeException failure) {
            log.error("Projection polling cycle failed", failure);
        }
    }
}
