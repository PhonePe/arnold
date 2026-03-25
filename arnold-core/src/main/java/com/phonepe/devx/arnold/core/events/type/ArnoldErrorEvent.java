package com.phonepe.devx.arnold.core.events.type;

import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.events.BaseEvent;
import com.phonepe.devx.arnold.core.events.EventType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArnoldErrorEvent implements BaseEvent {
    private final ArnoldErrorCode errorCode;
    private final String message;

    @Override
    public EventType getEventType() {
        return EventType.EXCEPTION;
    }
}
