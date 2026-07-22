package com.phonepe.devx.arnold.models.analysis;

import java.util.List;

public record AnalysisResponse(
        String mergeRequestId,
        String reviewState,
        String runId,
        List<String> personaIds,
        String runStatus,
        int completedPersonas,
        long createdAt,
        long updatedAt
) {
}
