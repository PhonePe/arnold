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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

/**
 * Legacy per-user collection of groups, superseded by per-group {@link MergeRequestGroup} records.
 * Retained only so the housekeeping migration can read existing data.
 */
public record MergeRequestGroupCollection(
        String id,
        List<MergeRequestGroup> groups,
        long updatedAt
) implements Id {
    @JsonCreator
    public MergeRequestGroupCollection(@JsonProperty("id") final String id,
                                       @JsonProperty("groups") final List<MergeRequestGroup> groups,
                                       @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.updatedAt = updatedAt;
    }
}
