package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.GodricService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Singleton
@ClientDeclaration("GODRIC")
public class GodricServiceHttpImpl extends BaseServiceClient implements GodricService {

    private static final String SERVICE_NAME = "GODRIC";

    private final OpenCodeConfiguration config;

    @Inject
    public GodricServiceHttpImpl(final EventIngestor eventIngestor, final OpenCodeConfiguration config) {
        super(SERVICE_NAME, eventIngestor);
        this.config = config;
    }

    @Override
    public List<GodricLLMEntity> listLLMs() {
        try {
            final var entities = executorFactory
                    .<List<GodricLLMEntity>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<>() {})
                    .url("/v1/entity/llm/" + config.getGodricNamespace() + "?skipInactive=true")
                    .command("listLLMs")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse, "namespace=" + config.getGodricNamespace(), null))
                    .build()
                    .executeTracked();
            return entities == null ? List.of() : entities;
        } catch (Exception e) {
            log.error("Failed to list LLMs from godric for namespace={}", config.getGodricNamespace(), e);
            return List.of();
        }
    }
}
