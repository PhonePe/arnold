package com.phonepe.arnold.core.runner;

import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Adds execution credentials to a job and submits it to the configured runner mode.
 */
@Slf4j
@Singleton
public class RunnerDispatcherImpl implements RunnerDispatcher {

    private final RunnerBackends backends;
    private final CopilotExecutionService copilot;

    @Inject
    public RunnerDispatcherImpl(final RunnerBackends backends, final CopilotExecutionService copilot) {
        this.backends = backends;
        this.copilot = copilot;
    }

    @Override
    public long submit(final Map<String, String> original) {
        final var variables = copilot.pipelineVariables(original);
        try {
            return backends.get().submit(variables);
        } catch (RuntimeException failure) {
            copilot.revoke(variables);
            log.error("Runner dispatch failed: workflow={}", variables.get("WORKFLOW"), failure);
            throw new IllegalStateException("Failed to dispatch runner job", failure);
        }
    }

    @Override
    public String jobUrl(final long jobId) {
        try {
            return backends.get().jobUrl(jobId);
        } catch (RuntimeException misconfigured) {
            return null;
        }
    }
}
