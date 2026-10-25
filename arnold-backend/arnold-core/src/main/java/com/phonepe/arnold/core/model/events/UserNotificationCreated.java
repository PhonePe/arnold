package com.phonepe.arnold.core.model.events;

import com.phonepe.arnold.core.events.ArnoldSignal;
import com.phonepe.arnold.core.model.notification.UserNotification;

public record UserNotificationCreated(
        String id,
        UserNotification notification,
        long occurredAt
) implements ArnoldSignal {
}
