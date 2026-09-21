package com.phonepe.devx.arnold.models.analysis;

import java.util.List;

public record AnalysisResponse(
        String mergeRequestId,
        String reviewState,
        String runId,
        List<String> personaIds,
        String runStatus,
        int completedPersonas,
        Long pipelineId,
        String pipelineUrl,
        String analysisOutcome,
        String outcomeDetail,
        List<AnalysisPersonaResponse> reviewPersonas,
        long createdAt,
        long updatedAt
) {
    public AnalysisResponse {
        reviewPersonas = reviewPersonas == null ? List.of() : List.copyOf(reviewPersonas);
    }
}
