package com.phonepe.devx.arnold.models.es;

import java.util.List;

/**
 * One persona-review run. {@code personaVersions} holds {@code personaId@version} so adoption and
 * effectiveness can be bucketed per persona revision.
 */
public record AnalysisRunDocument(
        String id,
        String type,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String status,
        String outcome,
        String invokedBy,
        List<String> personaIds,
        List<String> personaVersions,
        List<String> reviewPersonaIds,
        int personaCount,
        int succeededPersonas,
        int failedPersonas,
        int findingCount,
        int postedFindings,
        int failedFindings,
        String harness,
        String model,
        Long pipelineId,
        String failureDetail,
        long createdAt,
        long updatedAt,
        long durationMs,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Long cacheReadTokens
) {
    public static final String DOCUMENT_TYPE = "analysis_run";
}
