package com.phonepe.devx.arnold.core.clients;

import com.phonepe.platform.http.v2.executor.ExtractedResponse;
import com.phonepe.platform.http.v2.executor.HeaderPair;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import com.phonepe.platform.http.v2.executor.httpdata.SerializableHttpData;
import lombok.extern.slf4j.Slf4j;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.events.Events;

import javax.ws.rs.core.MediaType;

/**
 * Base class for all service clients in the Arnold application.
 * Provides common functionality such as handling non-success responses, event ingestion and creating header pairs.
 */
@Slf4j
public abstract class BaseServiceClient {

    private final String serviceClientName;
    protected HttpExecutorBuilderFactory executorFactory;
    protected EventIngestor eventIngestor;

    protected BaseServiceClient(final String serviceClientName,
                                final EventIngestor eventIngestor) {
        this.executorFactory = ClientHttpExecutorFactory.getExecutor(serviceClientName);
        this.serviceClientName = serviceClientName;
        this.eventIngestor = eventIngestor;
    }

    protected <T> T handleNonSuccessResponse(
            final ExtractedResponse extractedResponse,
            final Object context,
            final T defaultValue
    ) {
        log.error("[nonSuccessResponseConsumer] Error while hitting sample api for:{} responseCode:{} body:{}",
                context, extractedResponse.getCode(), new String(extractedResponse.getBody()));

        // todo check and change this logic appropriately
        eventIngestor.send(Events.createServiceExceptionEvent(
                serviceClientName,
                extractedResponse.getCode(),
                extractedResponse.getMessage()
        ));
        return defaultValue;
    }

    /**
     * helper method to create a new HeaderPair, that the sub classes can use
     */
    protected HeaderPair newHeaderPair(final String name, final String value) {
        return HeaderPair.builder()
                .name(name)
                .value(value)
                .build();
    }

    /**
     * helper method to create a new SerializableHttpData, that the sub classes can use
     */
    protected SerializableHttpData serializableHttpData(final Object data) {
        return SerializableHttpData.builder()
                .data(data)
                .mediaType(MediaType.APPLICATION_JSON)
                .build();
    }
}
