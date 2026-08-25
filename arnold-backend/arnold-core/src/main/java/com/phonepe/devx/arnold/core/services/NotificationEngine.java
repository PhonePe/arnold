package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.events.ArnoldDomainEvent;

public interface NotificationEngine {
    void record(ArnoldDomainEvent event);
}
