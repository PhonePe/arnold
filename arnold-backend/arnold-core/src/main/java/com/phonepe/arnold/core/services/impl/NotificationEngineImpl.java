/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.arnold.core.model.notification.UserNotification;
import com.phonepe.arnold.core.services.NotificationEngine;
import com.phonepe.arnold.core.services.NotificationInbox;

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
