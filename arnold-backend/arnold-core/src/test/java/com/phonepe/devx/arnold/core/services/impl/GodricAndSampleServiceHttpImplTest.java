package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.clients.ClientHttpExecutorFactory;
import com.phonepe.devx.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.GodricService;
import com.phonepe.platform.eventingestion.model.Event;
import com.phonepe.platform.http.v2.executor.Consumer;
import com.phonepe.platform.http.v2.executor.ExtractedResponse;
import com.phonepe.platform.http.v2.executor.HttpGetExecutor;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GodricAndSampleServiceHttpImplTest {
    private final EventIngestor eventIngestor = mock(EventIngestor.class);
    private final HttpExecutorBuilderFactory factory = mock(HttpExecutorBuilderFactory.class);
    private final HttpGetExecutor executor = mock(HttpGetExecutor.class);
    private final HttpGetExecutor.HttpGetExecutorBuilder builder = mock(HttpGetExecutor.HttpGetExecutorBuilder.class);
    private MockedStatic<ClientHttpExecutorFactory> mockedFactory;

    @BeforeEach
    void setUp() {
        mockedFactory = mockStatic(ClientHttpExecutorFactory.class);
        mockedFactory.when(() -> ClientHttpExecutorFactory.getExecutor(anyString())).thenReturn(factory);
        when(builder.url(anyString())).thenReturn(builder);
        when(builder.responseTypeReference(any())).thenReturn(builder);
        when(builder.responseType(any())).thenReturn(builder);
        when(builder.command(anyString())).thenReturn(builder);
        when(builder.nonSuccessResponseConsumer(any())).thenReturn(builder);
        when(builder.build()).thenReturn(executor);
        when(factory.httpGetExecutorBuilder()).thenReturn(builder);
    }

    @AfterEach
    void tearDown() {
        mockedFactory.close();
    }

    private GodricServiceHttpImpl godric() {
        final var config = new OpenCodeConfiguration();
        config.setGodricNamespace("ns");
        return new GodricServiceHttpImpl(eventIngestor, config);
    }

    @SuppressWarnings("unchecked")
    private Consumer<ExtractedResponse, Object> capturedNonSuccessConsumer() {
        final var captor = ArgumentCaptor.forClass(Consumer.class);
        verify(builder).nonSuccessResponseConsumer(captor.capture());
        return captor.getValue();
    }

    private static ExtractedResponse failure() {
        final var response = mock(ExtractedResponse.class);
        when(response.getCode()).thenReturn(500);
        when(response.getBody()).thenReturn("oops".getBytes());
        when(response.getMessage()).thenReturn("error");
        return response;
    }

    @Test
    void testListLLMs_shouldReturnEntities_andBuildNamespacedUrl() {
        final var entities = List.of(mock(GodricService.GodricLLMEntity.class));
        when(executor.executeTracked()).thenReturn(entities);

        assertEquals(entities, godric().listLLMs());
        verify(builder).url("/v1/entity/llm/ns?skipInactive=true");
    }

    @Test
    void testListLLMs_shouldReturnEmpty_whenResponseNull() {
        when(executor.executeTracked()).thenReturn(null);

        assertTrue(godric().listLLMs().isEmpty());
    }

    @Test
    void testListLLMs_shouldReturnEmpty_whenExecutionFails() {
        when(executor.executeTracked()).thenThrow(new IllegalStateException("down"));

        assertTrue(godric().listLLMs().isEmpty());
    }

    @Test
    void testListLLMs_nonSuccessConsumer_shouldIngestEvent() {
        when(executor.executeTracked()).thenReturn(List.of());
        godric().listLLMs();

        capturedNonSuccessConsumer().consume(failure());

        verify(eventIngestor).send(any(Event.class));
    }

    @Test
    void testSampleApi_shouldReturnExecutorResult_andIngestEventOnFailure() {
        when(executor.executeTracked()).thenReturn(true);
        final var service = new SampleServiceHttpImpl(eventIngestor);

        assertTrue(service.sampleApi("p"));
        verify(builder).url("/v1/some/path?queryParam=p");
        capturedNonSuccessConsumer().consume(failure());
        verify(eventIngestor).send(any(Event.class));
    }

    @Test
    void testSampleApi_shouldReturnFalse_whenExecutorReturnsFalse() {
        when(executor.executeTracked()).thenReturn(false);

        assertFalse(new SampleServiceHttpImpl(eventIngestor).sampleApi("p"));
    }
}
