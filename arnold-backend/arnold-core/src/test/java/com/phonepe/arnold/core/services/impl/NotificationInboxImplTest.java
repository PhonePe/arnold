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
import com.phonepe.arnold.core.model.notification.UserNotificationInbox;
import com.phonepe.arnold.core.storage.NotificationStore;
import com.phonepe.arnold.core.storage.VersionedValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationInboxImplTest {
    private InMemoryNotificationStore store;
    private NotificationInboxImpl inbox;

    @BeforeEach
    void setUp() {
        store = new InMemoryNotificationStore();
        inbox = new NotificationInboxImpl(store);
    }

    @Test
    void testAppend_shouldStoreOneCopy_whenEventIsDeliveredTwice() {
        final var notification = notification("notification-1", false, System.currentTimeMillis());

        final var first = inbox.append(notification);
        final var second = inbox.append(notification);

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(1, inbox.list("user-1").size());
    }

    @Test
    void testMarkAllRead_shouldUpdateTheInboxWithOneWrite() {
        inbox.append(notification("notification-1", false, System.currentTimeMillis()));
        inbox.append(notification("notification-2", false, System.currentTimeMillis() + 1));
        final var writesBeforeReceipt = store.writes;

        final var updated = inbox.markAllRead("user-1");

        assertTrue(updated.stream().allMatch(UserNotification::read));
        assertEquals(writesBeforeReceipt + 1, store.writes);
    }

    private UserNotification notification(final String id, final boolean read, final long createdAt) {
        return new UserNotification(id, "user-1", NotificationType.REVIEW_REQUESTED, "Review requested",
                "Change request", "change_request", "github:repo:1", "/review", read, 0, createdAt);
    }

    private static class InMemoryNotificationStore implements NotificationStore {
        private UserNotificationInbox value;
        private long version;
        private int writes;

        @Override
        public Optional<VersionedValue<UserNotificationInbox>> getVersioned(final String userId) {
            return Optional.ofNullable(value).map(item -> new VersionedValue<>(item, version));
        }

        @Override
        public void create(final UserNotificationInbox inbox) {
            value = inbox;
            version++;
            writes++;
        }

        @Override
        public void update(final UserNotificationInbox inbox, final Object expectedVersion) {
            value = inbox;
            version++;
            writes++;
        }
    }
}
