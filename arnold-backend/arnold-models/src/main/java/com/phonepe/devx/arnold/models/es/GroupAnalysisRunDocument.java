package com.phonepe.devx.arnold.models.es;

import java.util.List;

/**
 * One analysis run across a merge request group, as opposed to a single-MR {@link AnalysisRunDocument}.
 */
public record GroupAnalysisRunDocument(
        String id,
        String type,
        String groupId,
        String groupName,
        String status,
        String invokedBy,
        int memberCount,
        List<String> reviewPersonaIds,
        int findingCount,
        int postedFindings,
        int failedFindings,
        Long pipelineId,
        long createdAt,
        long updatedAt,
        long durationMs,
        Long totalTokens
) {
    public static final String DOCUMENT_TYPE = "group_analysis_run";
}
