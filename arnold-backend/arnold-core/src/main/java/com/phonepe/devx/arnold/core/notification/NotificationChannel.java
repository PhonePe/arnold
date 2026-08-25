package com.phonepe.devx.arnold.core.notification;

import com.phonepe.devx.arnold.core.model.notification.UserNotification;

public interface NotificationChannel {
    String type();

    DeliveryResult deliver(NotificationRecipient recipient, UserNotification notification);

    record NotificationRecipient(String userId, String externalId) {
    }

    record DeliveryResult(boolean delivered, String externalMessageId, String failure) {
    }
}
