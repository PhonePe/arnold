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

package com.phonepe.arnold.core.model.vcs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.Set;

public record UserChangeRequest(
        String id,
        String userId,
        String mergeRequestId,
        Set<MergeRequestRole> roles,
        boolean active,
        boolean open,
        long firstSeenAt,
        long lastSeenAt,
        long providerUpdatedAt
) implements Id {
    @JsonCreator
    public UserChangeRequest(@JsonProperty("id") final String id,
                             @JsonProperty("userId") final String userId,
                             @JsonProperty("mergeRequestId") final String mergeRequestId,
                             @JsonProperty("roles") final Set<MergeRequestRole> roles,
                             @JsonProperty("active") final boolean active,
                             @JsonProperty("open") final boolean open,
                             @JsonProperty("firstSeenAt") final long firstSeenAt,
                             @JsonProperty("lastSeenAt") final long lastSeenAt,
                             @JsonProperty("providerUpdatedAt") final long providerUpdatedAt) {
        this.id = id;
        this.userId = userId;
        this.mergeRequestId = mergeRequestId;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.active = active;
        this.open = open;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.providerUpdatedAt = providerUpdatedAt;
    }

    public static String key(final String userId, final String mergeRequestId) {
        return userId + ":" + mergeRequestId;
    }

    public static String userReference(final String userId) {
        return "user:" + userId;
    }

    public static String mergeRequestReference(final String mergeRequestId) {
        return "mr:" + mergeRequestId;
    }
}
