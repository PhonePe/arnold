package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldDomainEventBus;
import com.phonepe.devx.arnold.core.model.events.ArnoldDomainEvent;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;
import com.phonepe.devx.arnold.core.services.NotificationEngine;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeNotificationStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NotificationEngineImpl implements NotificationEngine {
    private final AerospikeNotificationStore notificationStore;

    @Inject
    public NotificationEngineImpl(final AerospikeNotificationStore notificationStore,
                                  final ArnoldDomainEventBus eventBus) {
        this.notificationStore = notificationStore;
        eventBus.connect("notification-engine", this::record);
    }

    @Override
    public void record(final ArnoldDomainEvent event) {
        event.recipientUserIds().forEach(userId -> {
            final var id = event.id() + ":" + userId;
            if (notificationStore.get(id).isPresent()) {
                return;
            }
            final var notification = new UserNotification(id, userId, event.type(), event.title(), event.body(),
                    "merge_request", event.mergeRequestId(), event.deepLink(), false, 0, event.occurredAt());
            try {
                notificationStore.createForUser(notification);
            } catch (RuntimeException race) {
                if (notificationStore.get(id).isEmpty()) {
                    throw race;
                }
            }
        });
    }
}
