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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabScmMetadataServiceTest {

    private WireMockServer server;
    private GitLabScmMetadataService service;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        service = newService("service-token");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private GitLabScmMetadataService newService(final String token) {
        final var configuration = new GitLabConfiguration(server.baseUrl(), "id", "secret", "cb", 1, 1, 1000,
                null, token);
        return new GitLabScmMetadataService(configuration, new ObjectMapper(), new OkHttpClient());
    }

    @Test
    void shouldMapProjectNamespaceObject() {
        server.stubFor(get(urlEqualTo("/api/v4/projects/7"))
                .withHeader("PRIVATE-TOKEN", equalTo("service-token"))
                .willReturn(aResponse().withBody("""
                        {"id":7,"name":"api","path_with_namespace":"acme/api",
                         "web_url":"https://scm.example.com/acme/api","namespace":{"full_path":"acme"}}
                        """)));

        final var project = service.getProject(7).orElseThrow();

        assertEquals("acme", project.getNamespace());
        assertEquals("acme/api", project.getPathWithNamespace());
    }

    @Test
    void shouldMapMergeRequestNote() {
        server.stubFor(get(urlEqualTo("/api/v4/projects/7/merge_requests/3/notes/11"))
                .willReturn(aResponse().withBody("""
                        {"id":11,"body":"nit","author":{"id":5,"username":"jane"},
                         "position":{"new_path":"a.java","new_line":4}}
                        """)));

        final var note = service.getMergeRequestNote(7, 3, 11).orElseThrow();

        assertEquals("jane", note.getAuthor().getUsername());
        assertEquals(4, note.getPosition().getNewLine());
    }

    @Test
    void shouldReturnEmpty_whenLookupFails() {
        server.stubFor(get(urlEqualTo("/api/v4/users/9")).willReturn(aResponse().withStatus(404)));

        assertTrue(service.getUser(9).isEmpty());
    }

    @Test
    void shouldSkipLookups_whenServiceTokenMissing() {
        assertTrue(newService(" ").getUser(9).isEmpty());
        assertEquals(0, server.getAllServeEvents().size());
    }
}
