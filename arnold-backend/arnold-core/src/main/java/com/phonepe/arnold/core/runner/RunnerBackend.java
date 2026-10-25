package com.phonepe.arnold.core.runner;

import java.util.Map;

/**
 * Starts a runner job in a specific execution environment.
 *
 * <p>A job is described entirely by string variables (for example {@code WORKFLOW}, {@code ARNOLD_RUN_ID}); every
 * mode hands the same variables to {@code arnold-runner} as environment variables.</p>
 */
public interface RunnerBackend {

    RunnerMode mode();

    /**
     * @return an identifier for the started job (pipeline id, workflow run id or queued job number)
     */
    long submit(Map<String, String> variables);

    /**
     * @return a web URL for the job, or null when the mode has none
     */
    String jobUrl(long jobId);
}
