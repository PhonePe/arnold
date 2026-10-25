package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.analysis.AnalysisResponse;
import com.phonepe.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.arnold.models.analysis.StartAnalysisRequest;

import java.util.List;

public interface AnalysisService {
    AnalysisResponse start(StartAnalysisRequest request, String userId);

    AnalysisResponse get(String runId);

    List<AnalysisResponse> list(String mergeRequestId, Long before, int limit);

    AnalysisResponse completePersona(PersonaCompletionRequest request);
}
