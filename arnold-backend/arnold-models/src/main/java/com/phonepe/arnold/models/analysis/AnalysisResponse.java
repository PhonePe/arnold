/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        ExecutionOptions execution,
        TokenUsage usage
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
                updatedAt, execution, null);
    }
}
