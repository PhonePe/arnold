package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;
import com.phonepe.platform.http.v2.executor.httpdata.FormHttpData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;

/**
 * GitLab API service client for triggering CI pipelines.
 *
 * <p>Declared as a service client so it reuses connection management, metrics and
 * non-success handling like other internal clients. Internal Olympus auth is disabled
 * because GitLab expects its own trigger token in the request body.</p>
 */
@Slf4j
@Singleton
@ClientDeclaration(value = "GITLAB", useInternalAuth = false)
public class PipelineTriggerClientImpl extends BaseServiceClient implements PipelineTriggerClient {

    private static final String SERVICE_NAME = "GITLAB";
    private final ClioPipelineConfiguration clio;
    private final String baseUrl;
    private final String projectPath;

    @Inject
    public PipelineTriggerClientImpl(final EventIngestor eventIngestor,
                                     final @Config GitLabConfiguration configuration) {
        super(SERVICE_NAME, eventIngestor);
        this.clio = configuration.clio();
        this.baseUrl = configuration.baseUrl() == null ? null : configuration.baseUrl().replaceAll("/+$", "");
        if (clio == null || clio.projectId() <= 0 || clio.projectPath() == null || clio.projectPath().isBlank()
                || clio.ref() == null || clio.ref().isBlank()
                || clio.triggerToken() == null || clio.triggerToken().isBlank()) {
            throw new IllegalStateException(
                    "Configure projectId, projectPath, ref and triggerToken in gitlabConfiguration.clio");
        }
        this.projectPath = clio.projectPath().replaceAll("^/+|/+$", "");
    }

    @Override
    public long triggerPipeline(final Map<String, String> variables) {
        final var projectId = clio.projectId();
        final var ref = clio.ref();
        final var formData = new ArrayList<FormHttpData.FormData>();
        formData.add(new FormHttpData.FormData("token", clio.triggerToken()));
        formData.add(new FormHttpData.FormData("ref", ref));
        variables.forEach((key, value) ->
                formData.add(new FormHttpData.FormData("variables[" + key + "]", value)));

        log.info("Triggering GitLab pipeline: project={}, ref={}, variables={}",
                projectId, ref, variables.keySet());

        final var response = executorFactory
                .<Map<String, Object>>httpPostExecutorBuilder()
                .responseTypeReference(new TypeReference<>() {})
                .url("/api/v4/projects/" + projectId + "/trigger/pipeline")
                .command("triggerPipeline")
                .httpData(FormHttpData.builder().formDataList(formData).build())
                .nonSuccessResponseConsumer(extractedResponse -> {
                    handleNonSuccessResponse(extractedResponse,
                            "projectId=" + projectId + ", ref=" + ref, null);
                    throw new RuntimeException("Failed to trigger GitLab pipeline: "
                            + extractedResponse.getCode());
                })
                .build()
                .executeTracked();

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException("Failed to trigger GitLab pipeline: missing pipeline id in response");
        }

        final long pipelineId = ((Number) response.get("id")).longValue();
        log.info("Pipeline triggered successfully: id={}", pipelineId);
        return pipelineId;
    }

    @Override
    public String pipelineUrl(final long pipelineId) {
        return baseUrl == null ? null : baseUrl + "/" + projectPath + "/-/pipelines/" + pipelineId;
    }
}
