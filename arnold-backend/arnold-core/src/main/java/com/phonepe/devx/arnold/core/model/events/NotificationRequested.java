package com.phonepe.devx.arnold.core.model.events;

import com.phonepe.devx.arnold.core.events.ArnoldSignal;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;

import java.util.Set;

public record NotificationRequested(
        String id,
        NotificationType type,
        String aggregateType,
        String aggregateId,
        Set<String> recipientUserIds,
        String title,
        String body,
        String deepLink,
        long occurredAt
) implements ArnoldSignal {
    public NotificationRequested {
        recipientUserIds = recipientUserIds == null ? Set.of() : Set.copyOf(recipientUserIds);
    }
}
