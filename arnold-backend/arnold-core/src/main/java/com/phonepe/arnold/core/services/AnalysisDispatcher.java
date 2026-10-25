package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;

public interface AnalysisDispatcher {
    long dispatch(String mergeRequestId, String runId, String personaId, String rfcUrl,
                  List<String> jiraTickets, String reviewContext, List<ReviewPersonaSnapshot> reviewPersonas);
}
