package com.phonepe.devx.arnold.core.model.events;

import com.phonepe.devx.arnold.core.model.notification.NotificationType;

import java.util.Set;

public record ArnoldDomainEvent(
        String id,
        NotificationType type,
        String mergeRequestId,
        Set<String> recipientUserIds,
        String title,
        String body,
        String deepLink,
        long occurredAt
) {
    public ArnoldDomainEvent {
        recipientUserIds = recipientUserIds == null ? Set.of() : Set.copyOf(recipientUserIds);
    }
}
