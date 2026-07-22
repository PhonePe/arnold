package com.phonepe.devx.arnold.server.events;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.dataplatform.EventIngestorClient;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ManagedEventIngestionClient implements Managed {

    @Inject
    private EventIngestorClient eventIngestorClient;

    @Override
    public void start() throws Exception {
        eventIngestorClient.start();
        log.info("Started event ingestion client");
    }

    @Override
    public void stop() throws Exception {
        eventIngestorClient.close();
        log.info("Stopped event ingestion client");
    }
}
