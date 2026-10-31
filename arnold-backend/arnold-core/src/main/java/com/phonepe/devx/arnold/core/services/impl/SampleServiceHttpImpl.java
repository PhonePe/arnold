package com.phonepe.devx.arnold.core.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.SampleService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ClientDeclaration("SAMPLE_SERVICE")
public class SampleServiceHttpImpl extends BaseServiceClient implements SampleService {

    @Inject
    public SampleServiceHttpImpl(EventIngestor eventIngestor) {
        super("SAMPLE_SERVICE", eventIngestor); // this is getting initialized directly from rangerHubConfiguration
    }

    @Override
    public boolean sampleApi(final String sampleParam) {
        return executorFactory.<Boolean>httpGetExecutorBuilder()
                .responseType(Boolean.class)
                .url("/v1/some/path?queryParam=" + sampleParam)
                .command("sampleApi")
                .nonSuccessResponseConsumer(extractedResponse ->
                        handleNonSuccessResponse(extractedResponse, sampleParam, false))
                .build()
                .executeTracked();
    }
}
