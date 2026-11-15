/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.ModelCatalog;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves statically configured models, falling back to an OpenAI-compatible {@code /models} endpoint.
 */
@Slf4j
@Singleton
public class OpenAiCompatibleModelCatalog implements ModelCatalog {

    private final OpenCodeConfiguration config;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;

    @Inject
    public OpenAiCompatibleModelCatalog(final OpenCodeConfiguration config, final ObjectMapper mapper) {
        this(config, mapper, HttpClients.create(15_000));
    }

    OpenAiCompatibleModelCatalog(final OpenCodeConfiguration config, final ObjectMapper mapper,
                                 final OkHttpClient httpClient) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<CatalogModel> models() {
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            return config.getModels().stream()
                    .filter(model -> model.getId() != null && !model.getId().isBlank())
                    .map(model -> new CatalogModel(model.getId(),
                            model.getName() == null ? model.getId() : model.getName()))
                    .toList();
        }
        if (config.getModelsUrl() == null || config.getModelsUrl().isBlank()) {
            return List.of();
        }
        return discover();
    }

    private List<CatalogModel> discover() {
        final var request = new Request.Builder()
                .url(HttpClients.trimTrailingSlash(config.getModelsUrl()) + "/models")
                .get();
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            request.header("Authorization", "Bearer " + config.getApiKey());
        }
        try (var response = httpClient.newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Model discovery failed: status={}", response.code());
                throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).build();
            }
            final var models = new ArrayList<CatalogModel>();
            for (final var node : mapper.readTree(response.body().byteStream()).path("data")) {
                final var id = node.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(new CatalogModel(id, id));
                }
            }
            return models;
        } catch (ArnoldError e) {
            throw e;
        } catch (Exception e) {
            log.warn("Model discovery failed", e);
            throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).build();
        }
    }
}
