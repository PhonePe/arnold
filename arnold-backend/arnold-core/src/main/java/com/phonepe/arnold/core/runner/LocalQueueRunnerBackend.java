package com.phonepe.arnold.core.runner;

import java.util.Map;

/**
 * Queues jobs in Arnold for self-hosted {@code arnold-runner worker} processes to claim.
 */
public class LocalQueueRunnerBackend implements RunnerBackend {

    private final RunnerJobQueue queue;

    public LocalQueueRunnerBackend(final RunnerJobQueue queue) {
        this.queue = queue;
    }

    @Override
    public RunnerMode mode() {
        return RunnerMode.LOCAL;
    }

    @Override
    public long submit(final Map<String, String> variables) {
        return queue.enqueue(variables).number();
    }

    @Override
    public String jobUrl(final long jobId) {
        return null;
    }
}
