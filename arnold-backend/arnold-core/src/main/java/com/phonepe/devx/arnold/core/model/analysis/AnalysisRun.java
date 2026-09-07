package com.phonepe.devx.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;
import java.util.Map;

public record AnalysisRun(
        String id,
        String mergeRequestId,
        List<String> personaIds,
        String invokedBy,
        String status,
        Map<String, PersonaReviewResult> results,
        Long pipelineId,
        String failureDetail,
        List<ReviewPersonaSnapshot> reviewPersonas,
        String requestFingerprint,
        long createdAt,
        long updatedAt
) implements Id {
    public AnalysisRun {
        personaIds = personaIds == null ? List.of() : List.copyOf(personaIds);
        results = results == null ? Map.of() : Map.copyOf(results);
        reviewPersonas = reviewPersonas == null ? List.of() : List.copyOf(reviewPersonas);
    }

    public AnalysisRun(final String id, final String mergeRequestId, final List<String> personaIds,
                       final String invokedBy, final String status, final Map<String, PersonaReviewResult> results,
                       final long createdAt, final long updatedAt) {
        this(id, mergeRequestId, personaIds, invokedBy, status, results, null, null, List.of(), null, createdAt,
                updatedAt);
    }
}
