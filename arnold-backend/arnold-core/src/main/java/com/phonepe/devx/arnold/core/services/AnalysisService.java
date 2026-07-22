package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.devx.arnold.models.analysis.StartAnalysisRequest;

public interface AnalysisService {
    AnalysisResponse start(StartAnalysisRequest request, String userId);

    AnalysisResponse get(String runId, String userId);

    AnalysisResponse completePersona(PersonaCompletionRequest request);
}
