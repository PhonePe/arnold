package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.notification.UserNotification;
import com.phonepe.arnold.core.services.NotificationInbox;
import com.phonepe.arnold.core.services.NotificationService;
import com.phonepe.arnold.models.notification.NotificationPageResponse;
import com.phonepe.arnold.models.notification.NotificationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;

@Singleton
public class NotificationServiceImpl implements NotificationService {
    private static final Comparator<UserNotification> NEWEST_FIRST =
            Comparator.comparingLong(UserNotification::createdAt).reversed()
                    .thenComparing(UserNotification::id, Comparator.reverseOrder());

    private final NotificationInbox inbox;

    @Inject
    public NotificationServiceImpl(final NotificationInbox inbox) {
        this.inbox = inbox;
    }

    @Override
    public NotificationPageResponse list(final String userId, final int requestedLimit, final String before) {
        final var limit = Math.max(1, Math.min(100, requestedLimit));
        final var cursor = decodeCursor(before);
        final var userNotifications = inbox.list(userId);
        final var all = userNotifications.stream()
                .filter(item -> cursor == null || after(item, cursor))
                .sorted(NEWEST_FIRST)
                .toList();
        final var page = all.stream().limit(limit).map(this::response).toList();
        final var next = all.size() > limit && !page.isEmpty()
                ? encodeCursor(page.get(page.size() - 1)) : null;
        final var unread = userNotifications.stream()
                .filter(item -> !item.read()).count();
        return new NotificationPageResponse(page, unread, next);
    }

    @Override
    public NotificationResponse markRead(final String userId, final String notificationId) {
        return inbox.markRead(userId, notificationId)
                .map(this::response)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    @Override
    public NotificationPageResponse markAllRead(final String userId) {
        inbox.markAllRead(userId);
        return list(userId, 20, null);
    }

    private NotificationResponse response(final UserNotification item) {
        return new NotificationResponse(item.id(), item.type().name(), item.title(), item.body(),
                item.aggregateType(), item.aggregateId(), item.deepLink(), item.read(), item.readAt(), item.createdAt());
    }

    private boolean after(final UserNotification notification, final Cursor cursor) {
        return notification.createdAt() < cursor.createdAt()
                || notification.createdAt() == cursor.createdAt() && notification.id().compareTo(cursor.id()) < 0;
    }

    private String encodeCursor(final NotificationResponse notification) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (notification.createdAt() + "\n" + notification.id()).getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(final String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            final var decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            final var separator = decoded.indexOf('\n');
            if (separator < 1 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException("Cursor fields are missing");
            }
            return new Cursor(Long.parseLong(decoded.substring(0, separator)), decoded.substring(separator + 1));
        } catch (RuntimeException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid cursor").build();
        }
    }

    private record Cursor(long createdAt, String id) {
    }
}
