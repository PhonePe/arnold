package com.phonepe.devx.arnold.core.model.events;

import com.phonepe.devx.arnold.core.events.ArnoldSignal;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;

public record UserNotificationCreated(
        String id,
        UserNotification notification,
        long occurredAt
) implements ArnoldSignal {
}
