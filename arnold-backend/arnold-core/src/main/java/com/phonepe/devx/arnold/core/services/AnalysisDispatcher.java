package com.phonepe.devx.arnold.core.services;

public interface AnalysisDispatcher {
    void dispatch(String mergeRequestId, String runId, String personaId);
}
