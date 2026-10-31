package com.phonepe.devx.arnold.core.events;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.dataplatform.EventIngestorClient;
import com.phonepe.platform.eventingestion.model.Event;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class EventIngestor {

    private final EventIngestorClient eventIngestorClient;
    @Named(BindingNames.EVENT_INGESTION)
    private final Supplier<Boolean> eventIngestionDisabledSupplier;

    public void send(final Event<?> event) {
        send(List.of(event));
    }

    public void send(final List<Event<?>> events) {
        if (null == eventIngestorClient || eventIngestionDisabledSupplier.get() || events == null) {
            return;
        }
        try {
            eventIngestorClient.send(List.copyOf(events));
        } catch (Exception e) {
            log.error("Error sending events: " + events, e);
        }
    }
}
