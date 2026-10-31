package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.devx.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;
import com.phonepe.devx.arnold.core.services.NotificationEngine;
import com.phonepe.devx.arnold.core.services.NotificationInbox;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NotificationEngineImpl implements NotificationEngine {
    private final NotificationInbox inbox;
    private final ArnoldSignalPublisher signals;

    @Inject
    public NotificationEngineImpl(final NotificationInbox inbox,
                                  final ArnoldSignalSubscriber signalSubscriber,
                                  final ArnoldSignalPublisher signals) {
        this.inbox = inbox;
        this.signals = signals;
        signalSubscriber.subscribe(NotificationRequested.class, "notification-engine", this::record);
    }

    @Override
    public void record(final NotificationRequested event) {
        event.recipientUserIds().forEach(userId -> {
            final var id = event.id() + ":" + userId;
            final var notification = new UserNotification(id, userId, event.type(), event.title(), event.body(),
                    event.aggregateType(), event.aggregateId(), event.deepLink(), false, 0, event.occurredAt());
            final var appended = inbox.append(notification);
            if (appended.created()) {
                signals.publish(new UserNotificationCreated(notification.id(), notification,
                        System.currentTimeMillis()));
            }
        });
    }
}
