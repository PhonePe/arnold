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

package com.phonepe.arnold.core.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubActionsRunnerBackendTest {

    private static final String DISPATCH = "/repos/acme/runner/actions/workflows/arnold-runner.yml/dispatches";

    private WireMockServer server;
    private InMemoryRunnerJobRepository store;
    private GitHubActionsRunnerBackend backend;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        store = new InMemoryRunnerJobRepository();
        final var config = new RunnerConfiguration.GitHub();
        config.setApiUrl(server.baseUrl());
        config.setRepository("acme/runner");
        config.setToken("gh-token");
        backend = new GitHubActionsRunnerBackend(config, new RunnerJobQueue(store, new RunnerConfiguration.Local()),
                new ObjectMapper(), new OkHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void queuesJobAndDispatchesOnlyItsNumber() {
        server.stubFor(post(urlEqualTo(DISPATCH)).willReturn(aResponse().withStatus(200)
                .withBody("{\"workflow_run_id\":987}")));

        final var runId = backend.submit(Map.of("WORKFLOW", "improve", "ARNOLD_COPILOT_KEY", "secret"));

        assertEquals(987, runId);
        final var job = store.list().get(0);
        assertEquals(RunnerJob.Status.QUEUED, job.status());
        server.verify(postRequestedFor(urlEqualTo(DISPATCH))
                .withHeader("Authorization", equalTo("Bearer gh-token"))
                .withRequestBody(equalToJson("{\"ref\":\"main\",\"inputs\":{\"job\":\"" + job.number()
                        + "\"},\"return_run_details\":true}")));
        assertFalse(server.getAllServeEvents().get(0).getRequest().getBodyAsString().contains("secret"));
        assertEquals("https://example.org/acme/runner/actions/runs/987".replace("https://example.org", server.baseUrl()),
                backend.jobUrl(987));
    }

    @Test
    void acceptsServersThatReturnNoRunDetails() {
        server.stubFor(post(urlEqualTo(DISPATCH)).willReturn(aResponse().withStatus(204)));

        assertEquals(0, backend.submit(Map.of("WORKFLOW", "improve")));
        assertEquals(server.baseUrl() + "/acme/runner/actions/workflows/arnold-runner.yml", backend.jobUrl(0));
    }

    @Test
    void failsWhenGitHubRejectsDispatch() {
        server.stubFor(post(urlEqualTo(DISPATCH)).willReturn(aResponse().withStatus(404)));

        assertThrows(IllegalStateException.class, () -> backend.submit(Map.of("WORKFLOW", "improve")));
    }

    @Test
    void derivesWebUrlForGitHubAndEnterprise() {
        assertEquals("https://github.com", GitHubActionsRunnerBackend.webUrl("https://api.github.com"));
        assertEquals("https://ghe.example.com", GitHubActionsRunnerBackend.webUrl("https://ghe.example.com/api/v3"));
    }
}
