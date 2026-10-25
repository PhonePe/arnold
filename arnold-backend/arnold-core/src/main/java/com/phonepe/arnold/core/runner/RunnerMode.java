package com.phonepe.arnold.core.runner;

/**
 * Where review workflows execute.
 */
public enum RunnerMode {
    /** Jobs are queued in Arnold and claimed by self-hosted {@code arnold-runner worker} processes. */
    LOCAL,
    /** Jobs run as GitLab CI pipelines triggered through the pipeline trigger API. */
    GITLAB,
    /** Jobs run as GitHub Actions workflows started through {@code workflow_dispatch}. */
    GITHUB
}
