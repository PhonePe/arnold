package com.phonepe.devx.arnold.core.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.phonepe.devx.arnold.core.common.Constants;

public interface BaseEvent {
    default String getHostname() {
        return Constants.HOSTNAME;
    }

    @JsonIgnore
    EventType getEventType();
}
