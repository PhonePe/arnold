package com.phonepe.devx.arnold.models.es;

/**
 * One finding produced by an analysis run, stored alongside {@link AnalysisRunDocument}. {@code personaId} is null for
 * findings from the general Improve pass.
 */
public record AnalysisFindingDocument(
        String id,
        String type,
        String runId,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String invokedBy,
        String personaId,
        Long personaVersion,
        String publicationStatus,
        String filePath,
        long createdAt
) {
    public static final String TYPE = "analysis_finding";

    public static String id(final String runId, final String findingId) {
        return "finding:" + runId + ":" + findingId;
    }
}
