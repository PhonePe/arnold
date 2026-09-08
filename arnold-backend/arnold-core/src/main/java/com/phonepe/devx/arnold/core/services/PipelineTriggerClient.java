package com.phonepe.devx.arnold.core.services;

import java.util.Map;

/**
 * GitLab API client used to trigger CI pipelines.
 */
public interface PipelineTriggerClient {

    /**
     * Trigger the configured Clio pipeline with variables.
     *
     * @param variables Pipeline variables
     * @return Pipeline ID
     */
    long triggerPipeline(Map<String, String> variables);
}
