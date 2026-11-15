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

package com.phonepe.arnold.core.model.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

public record UserNotification(
        String id,
        String userId,
        NotificationType type,
        String title,
        String body,
        String aggregateType,
        String aggregateId,
        String deepLink,
        boolean read,
        long readAt,
        long createdAt
) implements Id {
    @JsonCreator
    public UserNotification(@JsonProperty("id") final String id,
                            @JsonProperty("userId") final String userId,
                            @JsonProperty("type") final NotificationType type,
                            @JsonProperty("title") final String title,
                            @JsonProperty("body") final String body,
                            @JsonProperty("aggregateType") final String aggregateType,
                            @JsonProperty("aggregateId") final String aggregateId,
                            @JsonProperty("deepLink") final String deepLink,
                            @JsonProperty("read") final boolean read,
                            @JsonProperty("readAt") final long readAt,
                            @JsonProperty("createdAt") final long createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.deepLink = deepLink;
        this.read = read;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }
}
