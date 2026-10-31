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

    /**
     * @param pipelineId Pipeline ID returned by {@link #triggerPipeline(Map)}
     * @return GitLab web URL of the pipeline, or null when unavailable
     */
    String pipelineUrl(long pipelineId);
}
