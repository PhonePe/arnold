package com.phonepe.devx.arnold.core.storage;

import com.phonepe.devx.arnold.core.model.notification.UserNotificationInbox;

import java.util.Optional;

public interface NotificationStore {
    Optional<VersionedValue<UserNotificationInbox>> getVersioned(String userId);

    void create(UserNotificationInbox inbox);

    void update(UserNotificationInbox inbox, Object expectedVersion);
}
