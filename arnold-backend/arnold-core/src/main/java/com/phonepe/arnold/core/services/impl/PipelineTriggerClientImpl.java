package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.services.PipelineTriggerClient;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.io.IOException;
import java.util.Map;

/**
 * Triggers runner pipelines through the GitLab pipeline trigger API.
 */
@Slf4j
@Singleton
public class PipelineTriggerClientImpl implements PipelineTriggerClient {

    private final CopilotExecutionService copilot;
    private final ClioPipelineConfiguration clio;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String projectPath;

    @Inject
    public PipelineTriggerClientImpl(final @Config GitLabConfiguration configuration,
                                     final CopilotExecutionService copilot,
                                     final ObjectMapper mapper) {
        this(configuration, copilot, mapper, HttpClients.create(configuration.requestTimeoutMs()));
    }

    PipelineTriggerClientImpl(final GitLabConfiguration configuration,
                              final CopilotExecutionService copilot,
                              final ObjectMapper mapper,
                              final OkHttpClient httpClient) {
        this.copilot = copilot;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.clio = configuration.clio();
        this.baseUrl = HttpClients.trimTrailingSlash(configuration.baseUrl());
        if (clio == null || clio.projectId() <= 0 || clio.projectPath() == null || clio.projectPath().isBlank()
                || clio.ref() == null || clio.ref().isBlank()
                || clio.triggerToken() == null || clio.triggerToken().isBlank()) {
            throw new IllegalStateException(
                    "Configure projectId, projectPath, ref and triggerToken in gitlabConfiguration.clio");
        }
        this.projectPath = clio.projectPath().replaceAll("^/+|/+$", "");
    }

    @Override
    public long triggerPipeline(final Map<String, String> original) {
        final var variables = copilot.pipelineVariables(original);
        try {
            return dispatch(variables);
        } catch (Exception failure) {
            copilot.revoke(variables);
            throw new IllegalStateException("Failed to trigger GitLab pipeline", failure);
        }
    }

    private long dispatch(final Map<String, String> variables) throws IOException {
        final var form = new FormBody.Builder()
                .add("token", clio.triggerToken())
                .add("ref", clio.ref());
        variables.forEach((key, value) -> form.add("variables[" + key + "]", value));

        log.info("Triggering GitLab pipeline: project={}, ref={}, variables={}",
                clio.projectId(), clio.ref(), variables.keySet());

        final var request = new Request.Builder()
                .url(baseUrl + "/api/v4/projects/" + clio.projectId() + "/trigger/pipeline")
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
    public String pipelineUrl(final long pipelineId) {
        return baseUrl == null ? null : baseUrl + "/" + projectPath + "/-/pipelines/" + pipelineId;
    }
}
