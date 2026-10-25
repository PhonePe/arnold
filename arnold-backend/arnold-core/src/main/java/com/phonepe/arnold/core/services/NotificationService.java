package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.notification.NotificationPageResponse;
import com.phonepe.arnold.models.notification.NotificationResponse;

public interface NotificationService {
    NotificationPageResponse list(String userId, int limit, String before);

    NotificationResponse markRead(String userId, String notificationId);

    NotificationPageResponse markAllRead(String userId);
}
