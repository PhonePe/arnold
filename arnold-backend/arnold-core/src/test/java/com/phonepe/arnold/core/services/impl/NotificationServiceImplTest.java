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

import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.notification.UserNotification;
import com.phonepe.arnold.core.services.NotificationInbox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceImplTest {
    @Test
    void testList_shouldNotSkipNotificationsWithMatchingTimestamps() {
        final var inbox = new FixedNotificationInbox(List.of(
                notification("c", 100), notification("b", 100), notification("a", 100)));
        final var service = new NotificationServiceImpl(inbox);

        final var first = service.list("user-1", 2, null);
        final var second = service.list("user-1", 2, first.nextCursor());

        assertEquals(List.of("c", "b"), first.items().stream().map(item -> item.id()).toList());
        assertEquals(List.of("a"), second.items().stream().map(item -> item.id()).toList());
    }

    private UserNotification notification(final String id, final long createdAt) {
        return new UserNotification(id, "user-1", NotificationType.ASSIGNED, "Assigned", "Change request",
                "change_request", "github:repo:1", "/review", false, 0, createdAt);
    }

    private record FixedNotificationInbox(List<UserNotification> notifications) implements NotificationInbox {
        @Override
        public AppendResult append(final UserNotification notification) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserNotification> list(final String userId) {
            return notifications;
        }

        @Override
        public Optional<UserNotification> markRead(final String userId, final String notificationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UserNotification> markAllRead(final String userId) {
            throw new UnsupportedOperationException();
        }
    }
}
