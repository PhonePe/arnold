package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;

public interface AnalysisDispatcher {
    long dispatch(String mergeRequestId, String runId, String personaId, String rfcUrl,
                  List<String> jiraTickets, String reviewContext, List<ReviewPersonaSnapshot> reviewPersonas);
}
