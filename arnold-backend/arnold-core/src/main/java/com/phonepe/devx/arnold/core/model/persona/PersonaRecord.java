package com.phonepe.devx.arnold.core.model.persona;

import com.phonepe.devx.arnold.models.persona.PersonaStatus;
import com.phonepe.devx.arnold.models.persona.PersonaPolicy;
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
