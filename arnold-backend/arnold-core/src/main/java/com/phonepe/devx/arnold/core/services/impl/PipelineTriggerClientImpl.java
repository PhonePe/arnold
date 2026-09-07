package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.clients.HttpConfigurationHelper;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.phonepe.platform.http.v2.common.HttpConfiguration;
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
    private static final String ENV_GITLAB_TRIGGER_TOKEN = "GITLAB_TRIGGER_TOKEN";

    private final String triggerToken;

    @Inject
    public PipelineTriggerClientImpl(final EventIngestor eventIngestor,
                            final HttpConfigurationHelper httpConfigurationHelper) {
        super(SERVICE_NAME, eventIngestor);
        final HttpConfiguration configuration = httpConfigurationHelper.fetchHttpConfiguration(SERVICE_NAME);
        final var configToken = configuration.getAuthToken();
        this.triggerToken = configToken != null ? configToken : System.getenv(ENV_GITLAB_TRIGGER_TOKEN);
        if (this.triggerToken == null || this.triggerToken.isBlank()) {
            throw new IllegalStateException(
                    "GitLab trigger token not configured. Set " + ENV_GITLAB_TRIGGER_TOKEN
                            + " env var or authToken in GITLAB HTTP config.");
        }
    }

    @Override
    public long triggerPipeline(final String projectId,
                                final String ref,
                                final Map<String, String> variables) {
        final var formData = new ArrayList<FormHttpData.FormData>();
        formData.add(new FormHttpData.FormData("token", triggerToken));
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
}
