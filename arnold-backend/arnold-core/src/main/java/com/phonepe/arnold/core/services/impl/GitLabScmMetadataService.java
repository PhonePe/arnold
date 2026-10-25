/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.services.ScmMetadataService;
import com.phonepe.arnold.models.scm.Note;
import com.phonepe.arnold.models.scm.Project;
import com.phonepe.arnold.models.scm.User;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves SCM metadata directly from the GitLab REST API using the configured service token.
 */
@Slf4j
@Singleton
public class GitLabScmMetadataService implements ScmMetadataService {

    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final String apiUrl;
    private final String token;

    @Inject
    public GitLabScmMetadataService(final @Config GitLabConfiguration configuration, final ObjectMapper mapper) {
        this(configuration, mapper, HttpClients.create(configuration.requestTimeoutMs()));
    }

    GitLabScmMetadataService(final GitLabConfiguration configuration,
                             final ObjectMapper mapper,
                             final OkHttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.apiUrl = HttpClients.trimTrailingSlash(configuration.baseUrl()) + "/api/v4";
        this.token = configuration.serviceToken();
        if (token == null || token.isBlank()) {
            log.warn("gitlabConfiguration.serviceToken is not set; review feedback enrichment is disabled");
        }
    }

    @Override
    public Optional<Project> getProject(final int projectId) {
        return get("/projects/" + projectId, node -> Project.builder()
                .id(node.path("id").asInt())
                .name(text(node, "name"))
                .description(text(node, "description"))
                .webUrl(text(node, "web_url"))
                .namespace(node.path("namespace").isObject()
                        ? text(node.path("namespace"), "full_path")
                        : text(node, "namespace"))
                .pathWithNamespace(text(node, "path_with_namespace"))
                .build());
    }

    @Override
    public Optional<User> getUser(final int userId) {
        return get("/users/" + userId, node -> mapper.convertValue(node, User.class));
    }

    @Override
    public Optional<Note> getMergeRequestNote(final int projectId, final int mrIid, final int noteId) {
        return get("/projects/" + projectId + "/merge_requests/" + mrIid + "/notes/" + noteId,
                node -> mapper.convertValue(node, Note.class));
    }

    private <T> Optional<T> get(final String path, final Function<JsonNode, T> mapping) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        final var request = new Request.Builder()
                .url(apiUrl + path)
                .header("PRIVATE-TOKEN", token)
                .get()
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("GitLab metadata lookup failed: path={} status={}", path, response.code());
                return Optional.empty();
            }
            return Optional.ofNullable(mapping.apply(mapper.readTree(response.body().byteStream())));
        } catch (Exception e) {
            log.error("GitLab metadata lookup failed: path={}", path, e);
            return Optional.empty();
        }
    }

    private static String text(final JsonNode node, final String field) {
        final var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
