package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldSignals;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.services.NotificationInbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEngineImplTest {
    @Test
    void testNotificationRequested_shouldPersistPerUserAndPublishCreatedSignal() {
        final var inbox = mock(NotificationInbox.class);
        when(inbox.append(any())).thenAnswer(invocation ->
                new NotificationInbox.AppendResult(invocation.getArgument(0), true));
        final var signals = new ArnoldSignals();
        final var created = new ArrayList<UserNotificationCreated>();
        signals.subscribe(UserNotificationCreated.class, "test", created::add);
        new NotificationEngineImpl(inbox, signals, signals);

        signals.publish(new NotificationRequested("review:github:repo:1:10", NotificationType.REVIEW_REQUESTED,
                "change_request", "github:repo:1", Set.of("user-1"), "Review requested", "Change request",
                "/review?provider=GITHUB&repositoryId=repo&number=1", 10));

        final var notification = ArgumentCaptor.forClass(
                com.phonepe.devx.arnold.core.model.notification.UserNotification.class);
        verify(inbox).append(notification.capture());
        assertEquals("review:github:repo:1:10:user-1", notification.getValue().id());
        assertEquals(1, created.size());
        assertEquals(notification.getValue(), created.get(0).notification());
    }
}
