package com.phonepe.arnold.core.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.Min;

/**
 * Selects and configures the runner execution mode. Only the block for the selected {@link #mode} is used.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunnerConfiguration {

    private RunnerMode mode = RunnerMode.LOCAL;

    @Valid
    private Local local = new Local();

    @Valid
    private GitLab gitlab = new GitLab();

    @Valid
    private GitHub github = new GitHub();

    @Data
    public static class Local {
        /** A claimed job is requeued when its worker stops heartbeating for this long. */
        @Min(30)
        private long leaseSeconds = 300;
        /** Total claims allowed per job before it is marked failed. */
        @Min(1)
        private int maxAttempts = 2;
    }

    @Data
    @ToString(exclude = "triggerToken")
    public static class GitLab {
        /** Defaults to {@code gitlabConfiguration.baseUrl}. */
        private String baseUrl;
        private long projectId;
        private String projectPath;
        private String ref = "main";
        private String triggerToken;
    }

    @Data
    @ToString(exclude = "token")
    public static class GitHub {
        private String apiUrl = "https://api.github.com";
        /** Runner repository as {@code owner/name}. */
        private String repository;
        /** Workflow file name or id, e.g. {@code arnold-runner.yml}. */
        private String workflow = "arnold-runner.yml";
        private String ref = "main";
        /** Token with {@code actions:write} on the runner repository. */
        private String token;
        private long requestTimeoutMs = 30_000;
    }
}
