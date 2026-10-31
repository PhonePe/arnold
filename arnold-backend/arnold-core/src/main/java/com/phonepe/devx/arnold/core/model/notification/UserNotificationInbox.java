package com.phonepe.devx.arnold.core.model.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record UserNotificationInbox(
        String id,
        List<UserNotification> notifications,
        long updatedAt
) implements Id {
    @JsonCreator
    public UserNotificationInbox(@JsonProperty("id") final String id,
                                 @JsonProperty("notifications") final List<UserNotification> notifications,
                                 @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.notifications = notifications == null ? List.of() : List.copyOf(notifications);
        this.updatedAt = updatedAt;
    }
}
