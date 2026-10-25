package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.events.NotificationRequested;

public interface NotificationEngine {
    void record(NotificationRequested event);
}
