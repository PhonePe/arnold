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

package com.phonepe.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MergeQueueItem(
        String id,
        String targetType,
        String targetId,
        String role,
        String source,
        boolean blocked
) {
    @JsonCreator
    public MergeQueueItem(@JsonProperty("id") final String id,
                          @JsonProperty("targetType") final String targetType,
                          @JsonProperty("targetId") @JsonAlias("mergeRequestId") final String targetId,
                          @JsonProperty("role") final String role,
                          @JsonProperty("source") final String source,
                          @JsonProperty("blocked") final boolean blocked) {
        this.id = id;
        this.targetType = targetType == null ? "MERGE_REQUEST" : targetType;
        this.targetId = targetId;
        this.role = role;
        this.source = source == null ? "MANUAL" : source;
        this.blocked = blocked;
    }

    public MergeQueueItem(final String id, final String mergeRequestId, final String role, final boolean blocked) {
        this(id, "MERGE_REQUEST", mergeRequestId, role, "MANUAL", blocked);
    }

    public String mergeRequestId() {
        return targetId;
    }
}
