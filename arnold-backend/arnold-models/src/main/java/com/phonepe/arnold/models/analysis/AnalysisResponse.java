package com.phonepe.arnold.models.analysis;

import java.util.List;

public record AnalysisResponse(
        String mergeRequestId,
        String repositoryPath,
        String title,
        String invokedBy,
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
        List<AnalysisFindingResponse> findings,
        long createdAt,
        long updatedAt,
        ExecutionOptions execution
) {
    public AnalysisResponse {
        reviewPersonas = reviewPersonas == null ? List.of() : List.copyOf(reviewPersonas);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public AnalysisResponse(String mergeRequestId, String reviewState, String runId, List<String> personaIds,
                            String runStatus, int completedPersonas, Long pipelineId, String pipelineUrl,
                            String analysisOutcome, String outcomeDetail,
                            List<AnalysisPersonaResponse> reviewPersonas,
                            List<AnalysisFindingResponse> findings, long createdAt, long updatedAt,
                            ExecutionOptions execution) {
        this(mergeRequestId, null, null, null, reviewState, runId, personaIds, runStatus, completedPersonas,
                pipelineId, pipelineUrl, analysisOutcome, outcomeDetail, reviewPersonas, findings, createdAt,
                updatedAt, execution);
    }
}
