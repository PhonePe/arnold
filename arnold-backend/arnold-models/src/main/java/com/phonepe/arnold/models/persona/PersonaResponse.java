package com.phonepe.arnold.models.persona;

import java.util.List;

public record PersonaResponse(
        String id,
        String name,
        String summary,
        String instructions,
        PersonaPolicy policy,
        List<String> tags,
        String ownerId,
        List<String> contributorIds,
        PersonaStatus status,
        boolean systemOwned,
        long version,
        long createdAt,
        long updatedAt
) {
    public PersonaResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        contributorIds = contributorIds == null ? List.of() : List.copyOf(contributorIds);
    }
}
