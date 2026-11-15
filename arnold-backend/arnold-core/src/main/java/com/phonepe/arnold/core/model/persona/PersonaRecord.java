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

package com.phonepe.arnold.core.model.persona;

import com.phonepe.arnold.models.persona.PersonaStatus;
import com.phonepe.arnold.models.persona.PersonaPolicy;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

public record PersonaRecord(
        String id,
        String name,
        String summary,
        String instructions,
        PersonaPolicy policy,
        List<String> tags,
        String ownerId,
        List<String> contributorIds,
        PersonaStatus status,
        long version,
        long createdAt,
        long updatedAt
) implements Id {
    public PersonaRecord {
        tags = tags == null ? List.of() : List.copyOf(tags);
        contributorIds = contributorIds == null || contributorIds.isEmpty()
                ? List.of(ownerId) : List.copyOf(contributorIds);
    }
}
