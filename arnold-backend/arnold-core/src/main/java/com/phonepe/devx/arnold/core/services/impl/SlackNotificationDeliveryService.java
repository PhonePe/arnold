package com.phonepe.devx.arnold.core.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.config.SlackClientConfig;
import com.phonepe.devx.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.devx.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeUserStore;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Named;
import java.util.concurrent.ExecutorService;

@Singleton
@Slf4j
public class SlackNotificationDeliveryService {
    private final SlackService slack;
    private final AerospikeUserStore userStore;
    private final ExecutorService asyncExecutor;
    private final boolean enabled;

    @Inject
    public SlackNotificationDeliveryService(final SlackService slack,
                                            final AerospikeUserStore userStore,
                                            final ArnoldSignalSubscriber signalSubscriber,
                                            final @Named(BindingNames.ASYNC_TASK_EXECUTOR) ExecutorService asyncExecutor,
                                            final @Config SlackClientConfig config) {
        this.slack = slack;
        this.userStore = userStore;
        this.asyncExecutor = asyncExecutor;
        this.enabled = config != null && config.isEnabled();
        if (enabled) {
            signalSubscriber.subscribe(UserNotificationCreated.class, "slack-delivery", this::deliver);
        }
    }

    private void deliver(final UserNotificationCreated event) {
        final var notification = event.notification();
        if (notification.type() != NotificationType.ANALYSIS_FINISHED) {
            return;
        }
        final String deepLink = notification.deepLink() == null ? "" : notification.deepLink();
        final String linkPart = deepLink.isBlank() ? ""
                : " <" + deepLink + "|View in Arnold>";
        final String text = "*" + notification.title() + "*\n" + notification.body() + linkPart;
        asyncExecutor.execute(() -> send(notification.userId(), text));
    }

    private void send(final String userId, final String text) {
        try {
            final var user = userStore.get(userId).orElse(null);
            if (user == null || user.email() == null) {
                log.warn("slack_delivery skipped, no email for user {}", userId);
                return;
            }
            final var lookup = slack.usersLookupByEmail(user.email());
            if (!lookup.isOk() || lookup.getUser() == null) {
                log.warn("slack_delivery skipped, no slack user for email={}", user.email());
                return;
            }
            final var response = slack.chatPostMessage(lookup.getUser().getId(), text);
            if (!response.isOk()) {
                log.warn("slack_delivery failed for user={} error={}", userId, response.getError());
            }
        } catch (Exception e) {
            log.error("slack_delivery failed for user {}", userId, e);
        }
    }
}
