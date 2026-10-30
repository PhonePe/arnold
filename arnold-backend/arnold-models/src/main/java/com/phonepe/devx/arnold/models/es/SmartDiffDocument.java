package com.phonepe.devx.arnold.models.es;

public record SmartDiffDocument(
        String id,
        String type,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String headSha,
        String status,
        String requestedBy,
        int groupCount,
        String commentAssessmentStatus,
        int assessedCommentCount,
        String harness,
        String model,
        String errorMessage,
        long requestedAt,
        long updatedAt,
        long durationMs,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Long cacheReadTokens
) {
    public static final String DOCUMENT_TYPE = "smart_diff";
}
