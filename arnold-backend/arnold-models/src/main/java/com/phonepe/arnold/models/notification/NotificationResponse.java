package com.phonepe.arnold.models.notification;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String aggregateType,
        String aggregateId,
        String deepLink,
        boolean read,
        long readAt,
        long createdAt
) {
}
