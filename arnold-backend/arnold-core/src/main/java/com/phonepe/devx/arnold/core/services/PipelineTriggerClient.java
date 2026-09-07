package com.phonepe.devx.arnold.core.services;

import java.util.Map;

/**
 * GitLab API client used to trigger CI pipelines.
 */
public interface PipelineTriggerClient {

    /**
     * Trigger a GitLab CI pipeline with variables.
     *
     * @param projectId GitLab project ID
     * @param ref       Branch/tag to run pipeline on
     * @param variables Pipeline variables
     * @return Pipeline ID
     */
    long triggerPipeline(String projectId, String ref, Map<String, String> variables);
}
