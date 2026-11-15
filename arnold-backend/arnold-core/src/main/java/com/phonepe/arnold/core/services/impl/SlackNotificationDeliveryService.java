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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.guice.bindings.BindingNames;
import com.phonepe.arnold.core.config.SlackClientConfig;
import com.phonepe.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.notification.UserNotification;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeUserStore;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElements;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.header;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

@Singleton
@Slf4j
public class SlackNotificationDeliveryService {
    private final SlackService slack;
    private final AerospikeUserStore userStore;
    private final AerospikeChangeRequestStore changeRequestStore;
    private final ExecutorService asyncExecutor;
    private final boolean enabled;
    private final String uiBaseUrl;

    @Inject
    public SlackNotificationDeliveryService(final SlackService slack,
                                            final AerospikeUserStore userStore,
                                            final AerospikeChangeRequestStore changeRequestStore,
                                            final ArnoldSignalSubscriber signalSubscriber,
                                            final @Named(BindingNames.ASYNC_TASK_EXECUTOR) ExecutorService asyncExecutor,
                                            final @Config SlackClientConfig config) {
        this.slack = slack;
        this.userStore = userStore;
        this.changeRequestStore = changeRequestStore;
        this.asyncExecutor = asyncExecutor;
        this.enabled = config != null && config.isEnabled();
        this.uiBaseUrl = config != null && config.getUiBaseUrl() != null
                ? config.getUiBaseUrl().replaceAll("/+$", "") : "";
        if (enabled) {
            signalSubscriber.subscribe(UserNotificationCreated.class, "slack-delivery", this::deliver);
        }
    }

    private void deliver(final UserNotificationCreated event) {
        final var notification = event.notification();
        if (notification.type() != NotificationType.ANALYSIS_FINISHED) {
            return;
        }
        final var blocks = message(notification);
        asyncExecutor.execute(() -> send(notification.userId(), blocks));
    }

    private List<LayoutBlock> message(final UserNotification notification) {
        final var blocks = new ArrayList<LayoutBlock>();
        blocks.add(header(block -> block.text(plainText(notification.title()))));
        blocks.add(section(block -> block.text(markdownText(summaryOf(notification)))));
        if (notification.deepLink() != null && !notification.deepLink().isBlank()) {
            blocks.add(actions(block -> block.elements(List.of(
                    BlockElements.button(button -> button.text(plainText("View in Arnold"))
                            .url(uiBaseUrl + notification.deepLink())
                            .actionId("open_arnold"))))));
        }
        return blocks;
    }

    private String summaryOf(final UserNotification notification) {
        final var mrId = "smart_diff".equals(notification.aggregateType())
                ? smartDiffMergeRequestId(notification.aggregateId())
                : notification.body();
        final var mr = mrId == null ? null : changeRequestStore.get(mrId).orElse(null);
        if (mr != null) {
            return "*" + mr.repositoryPath() + " !"
                    + mr.number() + "* — " + mr.title();
        }
        final var body = notification.body() == null ? "" : notification.body();
        return body.isBlank() ? "" : "*" + body + "*";
    }

    private String smartDiffMergeRequestId(final String smartDiffId) {
        // id has five colon-separated parts: smart-diff, provider, projectId, iid, headSha
        final var parts = smartDiffId.split(":");
        return parts.length == 5 ? "gitlab:" + parts[2] + ":" + parts[3] : null;
    }

    private void send(final String userId, final List<LayoutBlock> blocks) {
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
            final var response = slack.chatPostBlockMessage(lookup.getUser().getId(), blocks);
            if (!response.isOk()) {
                log.warn("slack_delivery failed for user={} error={}", userId, response.getError());
            }
        } catch (Exception e) {
            log.error("slack_delivery failed for user {}", userId, e);
        }
    }
}
