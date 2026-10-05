package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.config.SlackClientConfig;
import com.phonepe.devx.arnold.core.events.ArnoldSignals;
import com.phonepe.devx.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;
import com.phonepe.devx.arnold.core.model.user.ArnoldUser;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeUserStore;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.User;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackNotificationDeliveryServiceTest {

    private final SlackService slack = mock(SlackService.class);
    private final AerospikeUserStore userStore = mock(AerospikeUserStore.class);
    private final ArnoldSignals signals = new ArnoldSignals();

    private void newService(final boolean enabled, final ArnoldUser user) {
        when(userStore.get(anyString())).thenReturn(Optional.ofNullable(user));
        final var executor = mock(ExecutorService.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        final var config = new SlackClientConfig();
        config.setEnabled(enabled);
        new SlackNotificationDeliveryService(slack, userStore, signals, executor, config);
    }

    private UserNotificationCreated event(final NotificationType type) {
        return new UserNotificationCreated("id-1",
                new UserNotification("id-1", "user-1", type, "AI review completed", "Analysis finished",
                        "analysis_run", "run-1", "/analysis?run=1", false, 0, 10), 10);
    }

    private ArnoldUser user() {
        return new ArnoldUser("user-1", "user@example.com", "user-1", "User One",
                Map.of(), Set.of(), 0, 0);
    }

    @Test
    void testAnalysisFinishedSendsBlockMessageWithButton() {
        newService(true, user());
        final var lookup = new UsersLookupByEmailResponse();
        lookup.setOk(true);
        final var slackUser = new User();
        slackUser.setId("U123");
        lookup.setUser(slackUser);
        when(slack.usersLookupByEmail("user@example.com")).thenReturn(lookup);
        final var post = new ChatPostMessageResponse();
        post.setOk(true);
        when(slack.chatPostBlockMessage(anyString(), anyList())).thenReturn(post);

        signals.publish(event(NotificationType.ANALYSIS_FINISHED));

        await().untilAsserted(() -> {
            verify(slack).usersLookupByEmail("user@example.com");
            verify(slack).chatPostBlockMessage(eq("U123"), argThat(blocks ->
                    blocks.size() == 3
                            && blocks.get(0).getType().equals("header")
                            && blocks.get(1).getType().equals("section")
                            && blocks.get(2).getType().equals("actions")));
        });
    }

    @Test
    void testNonAnalysisNotificationIsIgnored() {
        newService(true, user());

        signals.publish(event(NotificationType.MERGED));

        verify(slack, never()).usersLookupByEmail(anyString());
        verify(slack, never()).chatPostBlockMessage(anyString(), anyList());
    }

    @Test
    void testLookupMissSkipsSend() {
        newService(true, user());
        final var lookup = new UsersLookupByEmailResponse();
        lookup.setOk(false);
        lookup.setError("users_not_found");
        when(slack.usersLookupByEmail("user@example.com")).thenReturn(lookup);

        signals.publish(event(NotificationType.ANALYSIS_FINISHED));

        await().untilAsserted(() ->
                verify(slack, never()).chatPostBlockMessage(anyString(), anyList()));
    }

    @Test
    void testMissingEmailSkipsSend() {
        final var noEmail = new ArnoldUser("user-1", null, "user-1", "User One",
                Map.of(), Set.of(), 0, 0);
        newService(true, noEmail);

        signals.publish(event(NotificationType.ANALYSIS_FINISHED));

        await().untilAsserted(() ->
                verify(slack, never()).chatPostBlockMessage(anyString(), anyList()));
    }
}
