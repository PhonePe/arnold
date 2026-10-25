/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.arnold.core.events.ArnoldSignals;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.events.UserNotificationCreated;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.services.NotificationInbox;
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
                com.phonepe.arnold.core.model.notification.UserNotification.class);
        verify(inbox).append(notification.capture());
        assertEquals("review:github:repo:1:10:user-1", notification.getValue().id());
        assertEquals(1, created.size());
        assertEquals(notification.getValue(), created.get(0).notification());
    }
}
