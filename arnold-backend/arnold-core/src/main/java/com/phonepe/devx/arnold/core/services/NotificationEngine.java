package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.events.NotificationRequested;

public interface NotificationEngine {
    void record(NotificationRequested event);
}
