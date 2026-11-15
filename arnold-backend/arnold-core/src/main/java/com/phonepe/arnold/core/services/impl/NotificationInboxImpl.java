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

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.notification.UserNotification;
import com.phonepe.arnold.core.model.notification.UserNotificationInbox;
import com.phonepe.arnold.core.services.NotificationInbox;
import com.phonepe.arnold.core.storage.NotificationStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Singleton
public class NotificationInboxImpl implements NotificationInbox {
    private static final int MAX_UPDATE_ATTEMPTS = 8;
    private static final int MAX_NOTIFICATIONS = 200;
    private static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private final NotificationStore store;

    @Inject
    public NotificationInboxImpl(final NotificationStore store) {
        this.store = store;
    }

    @Override
    public AppendResult append(final UserNotification notification) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(notification.userId());
            final var notifications = current.map(item -> retained(item.value().notifications()))
                    .orElseGet(List::of);
            final var existing = notifications.stream()
                    .filter(item -> notification.id().equals(item.id()))
                    .findFirst();
            if (existing.isPresent()) {
                return new AppendResult(existing.get(), false);
            }
            final var updated = new ArrayList<>(notifications);
            updated.add(notification);
            updated.sort(Comparator.comparingLong(UserNotification::createdAt).reversed());
            final var inbox = new UserNotificationInbox(notification.userId(),
                    updated.stream().limit(MAX_NOTIFICATIONS).toList(), System.currentTimeMillis());
            final var persisted = current.isEmpty()
                    ? tryCreate(inbox)
                    : tryUpdate(inbox, current.get().version());
            if (persisted) {
                return new AppendResult(notification, true);
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private boolean tryCreate(final UserNotificationInbox inbox) {
        try {
            store.create(inbox);
            return true;
        } catch (RuntimeException createFailure) {
            if (store.getVersioned(inbox.id()).isEmpty()) {
                throw createFailure;
            }
            return false;
        }
    }

    private boolean tryUpdate(final UserNotificationInbox inbox, final Object version) {
        try {
            store.update(inbox, version);
            return true;
        } catch (ConcurrentUpdateException conflict) {
            // Rebuild the bounded inbox from the latest Aerospike generation.
            return false;
        }
    }

    @Override
    public List<UserNotification> list(final String userId) {
        return store.getVersioned(userId).map(item -> retained(item.value().notifications())).orElseGet(List::of);
    }

    @Override
    public Optional<UserNotification> markRead(final String userId, final String notificationId) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(userId);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            final var notifications = retained(current.get().value().notifications());
            final var target = notifications.stream().filter(item -> notificationId.equals(item.id())).findFirst();
            if (target.isEmpty() || target.get().read()) {
                return target;
            }
            final var readAt = System.currentTimeMillis();
            final var updatedNotification = read(target.get(), readAt);
            final var updated = notifications.stream()
                    .map(item -> notificationId.equals(item.id()) ? updatedNotification : item)
                    .toList();
            try {
                store.update(new UserNotificationInbox(userId, updated, readAt), current.get().version());
                return Optional.of(updatedNotification);
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the inbox before applying the receipt again.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    @Override
    public List<UserNotification> markAllRead(final String userId) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = store.getVersioned(userId);
            if (current.isEmpty()) {
                return List.of();
            }
            final var notifications = retained(current.get().value().notifications());
            if (notifications.stream().allMatch(UserNotification::read)) {
                return notifications;
            }
            final var readAt = System.currentTimeMillis();
            final var updated = notifications.stream()
                    .map(item -> item.read() ? item : read(item, readAt))
                    .toList();
            try {
                store.update(new UserNotificationInbox(userId, updated, readAt), current.get().version());
                return updated;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the inbox before applying the receipts again.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private List<UserNotification> retained(final List<UserNotification> notifications) {
        final var cutoff = System.currentTimeMillis() - RETENTION_MS;
        return notifications.stream()
                .filter(item -> item.createdAt() >= cutoff)
                .sorted(Comparator.comparingLong(UserNotification::createdAt).reversed())
                .limit(MAX_NOTIFICATIONS)
                .toList();
    }

    private UserNotification read(final UserNotification notification, final long readAt) {
        return new UserNotification(notification.id(), notification.userId(), notification.type(),
                notification.title(), notification.body(), notification.aggregateType(), notification.aggregateId(),
                notification.deepLink(), true, readAt, notification.createdAt());
    }
}
