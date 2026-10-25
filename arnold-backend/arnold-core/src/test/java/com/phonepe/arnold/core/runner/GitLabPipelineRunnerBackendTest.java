package com.phonepe.arnold.core.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabPipelineRunnerBackendTest {

    private static final String TRIGGER_PATH = "/api/v4/projects/42/trigger/pipeline";

    private WireMockServer server;
    private GitLabPipelineRunnerBackend backend;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        backend = newBackend(config -> { });
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private GitLabPipelineRunnerBackend newBackend(final Consumer<RunnerConfiguration.GitLab> customizer) {
        final var config = new RunnerConfiguration.GitLab();
        config.setBaseUrl(server.baseUrl() + "/");
        config.setProjectId(42);
        config.setProjectPath("acme/runner");
        config.setRef("main");
        config.setTriggerToken("trigger-token");
        customizer.accept(config);
        return new GitLabPipelineRunnerBackend(config, new ObjectMapper(), new OkHttpClient());
    }

    @Test
    void shouldReturnPipelineId_whenTriggerSucceeds() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withBody("{\"id\":12345}")));

        assertEquals(12345L, backend.submit(Map.of("WORKFLOW", "improve")));

        server.verify(postRequestedFor(urlEqualTo(TRIGGER_PATH))
                .withRequestBody(containing("token=trigger-token"))
                .withRequestBody(containing("ref=main"))
                .withRequestBody(containing("variables%5BWORKFLOW%5D=improve")));
    }

    @Test
    void shouldThrow_whenResponseDoesNotContainId() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withBody("{}")));

        assertThrows(IllegalStateException.class, () -> backend.submit(Map.of()));
    }

    @Test
    void shouldThrow_whenGitLabRejectsTrigger() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withStatus(401)));

        assertThrows(IllegalStateException.class, () -> backend.submit(Map.of()));
    }

    @Test
    void jobUrl_shouldUseCanonicalProjectPath() {
        assertEquals(server.baseUrl() + "/acme/runner/-/pipelines/6058966", backend.jobUrl(6058966));
    }

    @Test
    void constructor_shouldRejectIncompleteConfiguration() {
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setTriggerToken(" ")));
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setTriggerToken(null)));
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setRef(" ")));
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setProjectPath(null)));
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setProjectId(0)));
        assertThrows(IllegalStateException.class, () -> newBackend(c -> c.setBaseUrl(null)));
    }
}
