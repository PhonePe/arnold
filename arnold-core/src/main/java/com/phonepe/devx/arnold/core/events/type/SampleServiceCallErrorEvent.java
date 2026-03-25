package com.phonepe.devx.arnold.core.events.type;

import com.phonepe.devx.arnold.core.events.BaseEvent;
import com.phonepe.devx.arnold.core.events.EventType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SampleServiceCallErrorEvent implements BaseEvent {
    private final String serviceName;
    private final int responseCode;
    private final String message;

    @Override
    public EventType getEventType() {
        return EventType.EXCEPTION;
    }
}
