package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.notification.UserNotification;

import java.util.List;
import java.util.Optional;

public interface NotificationInbox {
    AppendResult append(UserNotification notification);

    List<UserNotification> list(String userId);

    Optional<UserNotification> markRead(String userId, String notificationId);

    List<UserNotification> markAllRead(String userId);

    record AppendResult(UserNotification notification, boolean created) {
    }
}
