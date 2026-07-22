package com.phonepe.devx.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;

import java.util.List;
import java.util.Map;

public record AnalysisRun(
        String id,
        String mergeRequestId,
        List<String> personaIds,
        String invokedBy,
        String status,
        Map<String, PersonaReviewResult> results,
        long createdAt,
        long updatedAt
) implements Id {
    public AnalysisRun {
        personaIds = personaIds == null ? List.of() : List.copyOf(personaIds);
        results = results == null ? Map.of() : Map.copyOf(results);
    }
}
