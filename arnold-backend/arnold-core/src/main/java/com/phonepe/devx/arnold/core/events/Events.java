package com.phonepe.devx.arnold.core.events;

import com.phonepe.platform.eventingestion.model.Event;
import com.phonepe.devx.arnold.core.events.type.ArnoldErrorEvent;
import com.phonepe.devx.arnold.core.events.type.SampleServiceCallErrorEvent;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Events {

    public Event<ArnoldErrorEvent> createExceptionEvent(final Exception e) {
        final ArnoldError error = ArnoldError.propagate(e).build();
        return EventMapper.event(ArnoldErrorEvent.builder()
                .errorCode(error.getErrorCode())
                .message(error.getMessage())
                .build());
    }

    public Event<SampleServiceCallErrorEvent> createServiceExceptionEvent(
            final String serviceName,
            final int responseCode,
            final String message
    ) {
        return EventMapper.event(SampleServiceCallErrorEvent.builder()
                .serviceName(serviceName)
                .responseCode(responseCode)
                .message(message)
                .build());
    }
}
