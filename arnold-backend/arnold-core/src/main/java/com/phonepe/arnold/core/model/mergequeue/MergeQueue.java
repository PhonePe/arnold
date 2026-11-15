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

package com.phonepe.arnold.core.model.mergequeue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record MergeQueue(String id, List<MergeQueueItem> items, long updatedAt, List<String> priorityOrder) implements Id {

    @JsonCreator
    public MergeQueue(@JsonProperty("id") final String id,
                      @JsonProperty("items") final List<MergeQueueItem> items,
                      @JsonProperty("updatedAt") final long updatedAt,
                      @JsonProperty("priorityOrder") final List<String> priorityOrder) {
        this.id = id;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.updatedAt = updatedAt;
        this.priorityOrder = priorityOrder == null ? List.of() : List.copyOf(priorityOrder);
    }
    public MergeQueue(final String id, final List<MergeQueueItem> items, final long updatedAt) {
        this(id, items, updatedAt, List.of());
    }
}
