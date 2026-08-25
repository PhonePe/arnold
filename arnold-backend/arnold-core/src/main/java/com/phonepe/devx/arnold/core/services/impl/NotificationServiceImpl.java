package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;
import com.phonepe.devx.arnold.core.services.NotificationService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeNotificationStore;
import com.phonepe.devx.arnold.models.notification.NotificationPageResponse;
import com.phonepe.devx.arnold.models.notification.NotificationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;

@Singleton
public class NotificationServiceImpl implements NotificationService {
    private final AerospikeNotificationStore store;

    @Inject
    public NotificationServiceImpl(final AerospikeNotificationStore store) {
        this.store = store;
    }

    @Override
    public NotificationPageResponse list(final String userId, final int requestedLimit, final String before) {
        final var limit = Math.max(1, Math.min(100, requestedLimit));
        final var beforeTimestamp = decodeCursor(before);
        final var userNotifications = store.getByRefId(UserNotification.userReference(userId));
        final var all = userNotifications.stream()
                .filter(item -> beforeTimestamp == 0 || item.createdAt() < beforeTimestamp)
                .sorted(Comparator.comparingLong(UserNotification::createdAt).reversed())
                .toList();
        final var page = all.stream().limit(limit).map(this::response).toList();
        final var next = all.size() > limit && !page.isEmpty()
                ? encodeCursor(page.get(page.size() - 1).createdAt()) : null;
        final var unread = userNotifications.stream()
                .filter(item -> !item.read()).count();
        return new NotificationPageResponse(page, unread, next);
    }

    @Override
    public NotificationResponse markRead(final String userId, final String notificationId) {
        final var current = store.getWithVersion(notificationId)
                .filter(item -> userId.equals(item.value().userId()))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (current.value().read()) {
            return response(current.value());
        }
        final var updated = read(current.value());
        try {
            store.checkAndUpdate(updated, current.version());
            return response(updated);
        } catch (ConcurrentUpdateException conflict) {
            return store.get(notificationId).filter(item -> userId.equals(item.userId()))
                    .map(this::response)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build());
        }
    }

    @Override
    public NotificationPageResponse markAllRead(final String userId) {
        store.getByRefId(UserNotification.userReference(userId)).stream()
                .filter(item -> !item.read())
                .forEach(item -> markRead(userId, item.id()));
        return list(userId, 20, null);
    }

    private UserNotification read(final UserNotification item) {
        return new UserNotification(item.id(), item.userId(), item.type(), item.title(), item.body(),
                item.aggregateType(), item.aggregateId(), item.deepLink(), true, System.currentTimeMillis(),
                item.createdAt());
    }

    private NotificationResponse response(final UserNotification item) {
        return new NotificationResponse(item.id(), item.type().name(), item.title(), item.body(),
                item.aggregateType(), item.aggregateId(), item.deepLink(), item.read(), item.readAt(), item.createdAt());
    }

    private String encodeCursor(final long timestamp) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
    }

    private long decodeCursor(final String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (RuntimeException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid cursor").build();
        }
    }
}
