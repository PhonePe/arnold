package com.phonepe.arnold.core.services;

import java.util.Map;

/**
 * Dispatches review workflow jobs to the configured runner mode.
 */
public interface RunnerDispatcher {

    /**
     * @param variables job variables, exposed to the runner as environment variables
     * @return the job id assigned by the runner mode
     */
    long submit(Map<String, String> variables);

    /**
     * @return a web URL for the job, or null when unavailable
     */
    String jobUrl(long jobId);
}
