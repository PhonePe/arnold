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

package com.phonepe.arnold.core.model.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

public record ClusterControlRecord(
        String id,
        String kind,
        String value,
        String ownerId,
        long leaseUntil,
        long updatedAt
) implements Id {
    @JsonCreator
    public ClusterControlRecord(@JsonProperty("id") final String id,
                                @JsonProperty("kind") final String kind,
                                @JsonProperty("value") final String value,
                                @JsonProperty("ownerId") final String ownerId,
                                @JsonProperty("leaseUntil") final long leaseUntil,
                                @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.kind = kind;
        this.value = value;
        this.ownerId = ownerId;
        this.leaseUntil = leaseUntil;
        this.updatedAt = updatedAt;
    }

    public static String settingId(final String name) {
        return "setting:" + name;
    }

    public static String leaderId(final String name) {
        return "leader:" + name;
    }
}
