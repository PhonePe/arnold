package com.phonepe.devx.arnold.models.notification;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        long unreadCount,
        String nextCursor
) {
    public NotificationPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
