package com.phonepe.devx.arnold.core.notification;

import com.phonepe.devx.arnold.core.model.notification.UserNotification;

import javax.inject.Singleton;

@Singleton
public class SlackNotificationRenderer {
    public SlackMessage render(final String applicationBaseUrl, final UserNotification notification) {
        final var base = applicationBaseUrl == null ? "" : applicationBaseUrl.replaceAll("/+$", "");
        return new SlackMessage(notification.title(), notification.body(), base + notification.deepLink());
    }

    public record SlackMessage(String title, String body, String actionUrl) {
    }
}
