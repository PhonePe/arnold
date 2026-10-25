package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.phonepe.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineTriggerClientImplTest {

    private static final String TRIGGER_PATH = "/api/v4/projects/42/trigger/pipeline";

    private WireMockServer server;
    private CopilotExecutionService copilot;
    private PipelineTriggerClientImpl client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        copilot = mock(CopilotExecutionService.class);
        when(copilot.pipelineVariables(any())).thenAnswer(call -> call.getArgument(0));
        client = newClient(new ClioPipelineConfiguration(42L, "acme/runner", "main", "trigger-token"));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private PipelineTriggerClientImpl newClient(final ClioPipelineConfiguration clio) {
        final var configuration = new GitLabConfiguration(server.baseUrl() + "/", "id", "secret", "cb",
                1, 1, 1000, clio);
        return new PipelineTriggerClientImpl(configuration, copilot, new ObjectMapper(), new OkHttpClient());
    }

    @Test
    void shouldReturnPipelineId_whenTriggerSucceeds() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withBody("{\"id\":12345}")));

        assertEquals(12345L, client.triggerPipeline(Map.of("WORKFLOW", "improve")));

        server.verify(postRequestedFor(urlEqualTo(TRIGGER_PATH))
                .withRequestBody(containing("token=trigger-token"))
                .withRequestBody(containing("ref=main"))
                .withRequestBody(containing("variables%5BWORKFLOW%5D=improve")));
    }

    @Test
    void shouldThrowAndRevoke_whenResponseDoesNotContainId() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withBody("{}")));

        assertThrows(IllegalStateException.class, () -> client.triggerPipeline(Map.of()));
        verify(copilot).revoke(Map.of());
    }

    @Test
    void shouldThrow_whenGitLabRejectsTrigger() {
        server.stubFor(post(urlEqualTo(TRIGGER_PATH)).willReturn(aResponse().withStatus(401)));

        assertThrows(IllegalStateException.class, () -> client.triggerPipeline(Map.of()));
    }

    @Test
    void testPipelineUrl_shouldUseCanonicalProjectPath() {
        assertEquals(server.baseUrl() + "/acme/runner/-/pipelines/6058966", client.pipelineUrl(6058966));
    }

    @Test
    void testConstructor_shouldRejectIncompleteClioConfiguration() {
        for (final var clio : List.of(
                new ClioPipelineConfiguration(42L, "acme/runner", "main", "   "),
                new ClioPipelineConfiguration(42L, "acme/runner", "main", null),
                new ClioPipelineConfiguration(42L, "acme/runner", " ", "trigger-token"),
                new ClioPipelineConfiguration(42L, "acme/runner", null, "trigger-token"),
                new ClioPipelineConfiguration(42L, " ", "main", "trigger-token"),
                new ClioPipelineConfiguration(42L, null, "main", "trigger-token"),
                new ClioPipelineConfiguration(0L, "acme/runner", "main", "trigger-token"))) {
            assertThrows(IllegalStateException.class, () -> newClient(clio));
        }
    }

    @Test
    void testConstructor_shouldRejectMissingClioConfiguration() {
        assertThrows(IllegalStateException.class, () -> newClient(null));
    }
}
