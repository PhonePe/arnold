package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.devx.arnold.core.clients.ClientHttpExecutorFactory;
import com.phonepe.devx.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.platform.http.v2.executor.HttpPostExecutor;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.platform.http.v2.executor.httpdata.FormHttpData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineTriggerClientImplTest {

    @Mock
    private EventIngestor mockEventIngestor;

    @Mock
    private GitLabConfiguration configuration;

    @Mock
    private HttpExecutorBuilderFactory mockExecutorFactory;

    @Mock
    private HttpPostExecutor mockHttpPostExecutor;

    private PipelineTriggerClientImpl gitLabClient;
    private MockedStatic<ClientHttpExecutorFactory> mockedFactory;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(configuration.clio()).thenReturn(new ClioPipelineConfiguration(17352L, "sub-agents", "trigger-token"));
        mockedFactory = mockStatic(ClientHttpExecutorFactory.class);
        mockedFactory.when(() -> ClientHttpExecutorFactory.getExecutor("GITLAB"))
                .thenReturn(mockExecutorFactory);
        gitLabClient = new PipelineTriggerClientImpl(mockEventIngestor, configuration);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockedFactory != null) mockedFactory.close();
        if (mocks != null) mocks.close();
    }

    @SuppressWarnings("unchecked")
    private HttpPostExecutor.HttpPostExecutorBuilder<Map<String, Object>> stubBuilder() {
        final var builder = mock(HttpPostExecutor.HttpPostExecutorBuilder.class);
        when(builder.url(anyString())).thenReturn(builder);
        when(builder.command(anyString())).thenReturn(builder);
        when(builder.responseTypeReference(any(TypeReference.class))).thenReturn(builder);
        when(builder.httpData(any(FormHttpData.class))).thenReturn(builder);
        when(builder.nonSuccessResponseConsumer(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mockHttpPostExecutor);
        when(mockExecutorFactory.httpPostExecutorBuilder()).thenReturn(builder);
        return builder;
    }

    @Test
    void shouldReturnPipelineId_whenTriggerSucceeds() {
        final var builder = stubBuilder();
        when(mockHttpPostExecutor.executeTracked()).thenReturn(Map.of("id", 12345));

        final long pipelineId = gitLabClient.triggerPipeline(Map.of("WORKFLOW", "improve"));

        assertEquals(12345L, pipelineId);
        verify(builder).url("/api/v4/projects/17352/trigger/pipeline");
        final var form = ArgumentCaptor.forClass(FormHttpData.class);
        verify(builder).httpData(form.capture());
        assertEquals(List.of(new FormHttpData.FormData("token", "trigger-token"),
                new FormHttpData.FormData("ref", "sub-agents"),
                new FormHttpData.FormData("variables[WORKFLOW]", "improve")), form.getValue().getFormDataList());
    }

    @Test
    void shouldThrow_whenResponseDoesNotContainId() {
        stubBuilder();
        when(mockHttpPostExecutor.executeTracked()).thenReturn(Map.of());

        assertThrows(RuntimeException.class,
                () -> gitLabClient.triggerPipeline(Map.of()));
    }

    @Test
    void shouldThrow_whenResponseIsNull() {
        stubBuilder();
        when(mockHttpPostExecutor.executeTracked()).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> gitLabClient.triggerPipeline(Map.of()));
    }

    @Test
    void testConstructor_shouldRejectIncompleteClioConfiguration() {
        for (final var clio : List.of(
                new ClioPipelineConfiguration(17352L, "sub-agents", "   "),
                new ClioPipelineConfiguration(17352L, "sub-agents", null),
                new ClioPipelineConfiguration(17352L, " ", "trigger-token"),
                new ClioPipelineConfiguration(17352L, null, "trigger-token"),
                new ClioPipelineConfiguration(0L, "sub-agents", "trigger-token"))) {
            when(configuration.clio()).thenReturn(clio);
            assertThrows(IllegalStateException.class,
                    () -> new PipelineTriggerClientImpl(mockEventIngestor, configuration));
        }
    }

    @Test
    void testConstructor_shouldRejectMissingClioConfiguration() {
        when(configuration.clio()).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> new PipelineTriggerClientImpl(mockEventIngestor, configuration));
    }
}
