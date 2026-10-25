package com.phonepe.arnold.core.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.clients.HttpClients;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.util.Map;

/**
 * Runs jobs as GitLab CI pipelines in a runner project, started through the pipeline trigger API.
 */
@Slf4j
public class GitLabPipelineRunnerBackend implements RunnerBackend {

    private final RunnerConfiguration.GitLab config;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String projectPath;

    public GitLabPipelineRunnerBackend(final RunnerConfiguration.GitLab config, final ObjectMapper mapper,
                                       final OkHttpClient httpClient) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank() || config.getProjectId() <= 0
                || config.getProjectPath() == null || config.getProjectPath().isBlank()
                || config.getRef() == null || config.getRef().isBlank()
                || config.getTriggerToken() == null || config.getTriggerToken().isBlank()) {
            throw new IllegalStateException(
                    "runner.gitlab requires baseUrl, projectId, projectPath, ref and triggerToken");
        }
        this.config = config;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.baseUrl = HttpClients.trimTrailingSlash(config.getBaseUrl());
        this.projectPath = config.getProjectPath().replaceAll("^/+|/+$", "");
    }

    @Override
    public RunnerMode mode() {
        return RunnerMode.GITLAB;
    }

    @Override
    public long submit(final Map<String, String> variables) {
        try {
            return trigger(variables);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to trigger GitLab pipeline", failure);
        }
    }

    private long trigger(final Map<String, String> variables) throws IOException {
        final var form = new FormBody.Builder()
                .add("token", config.getTriggerToken())
                .add("ref", config.getRef());
        variables.forEach((key, value) -> form.add("variables[" + key + "]", value));

        log.info("Triggering GitLab pipeline: project={}, ref={}, variables={}",
                config.getProjectId(), config.getRef(), variables.keySet());

        final var request = new Request.Builder()
                .url(baseUrl + "/api/v4/projects/" + config.getProjectId() + "/trigger/pipeline")
                .post(form.build())
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Failed to trigger GitLab pipeline: " + response.code());
            }
            final JsonNode body = response.body() == null ? null : mapper.readTree(response.body().byteStream());
            if (body == null || !body.hasNonNull("id")) {
                throw new IllegalStateException("Failed to trigger GitLab pipeline: missing pipeline id in response");
            }
            final long pipelineId = body.get("id").asLong();
            log.info("Pipeline triggered successfully: id={}", pipelineId);
            return pipelineId;
        }
    }

    @Override
    public String jobUrl(final long jobId) {
        return baseUrl + "/" + projectPath + "/-/pipelines/" + jobId;
    }
}
