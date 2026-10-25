package com.phonepe.arnold.models.es;

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
        long durationMs
) {
    public static final String TYPE = "smart_diff";
}
