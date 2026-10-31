package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.devx.arnold.models.analysis.ReviewPersonaDefinition;
import com.phonepe.devx.arnold.models.analysis.StartAnalysisRequest;

import java.util.List;

public interface AnalysisService {
    AnalysisResponse start(StartAnalysisRequest request, String userId);

    AnalysisResponse get(String runId);

    List<AnalysisResponse> list(String mergeRequestId, Long before, int limit);

    AnalysisResponse completePersona(PersonaCompletionRequest request);

    /**
     * Persona definitions frozen on a running analysis. {@code refs} ({@code id@version,...}), when given, must match
     * the run's snapshot exactly so a pipeline from a superseded dispatch cannot pick up different personas.
     */
    List<ReviewPersonaDefinition> reviewPersonas(String runId, String refs);
}
