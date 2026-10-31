package com.phonepe.devx.arnold.models.es;

/**
 * Per-reviewer, per-head-SHA summary of how a reviewer used the Arnold review workspace.
 */
public record ReviewEngagementDocument(
        String id,
        String userId,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String headSha,
        int filesViewed,
        int storyStepsCompleted,
        int focusAreasRated,
        int focusAreasUseful,
        int focusAreasNotUseful,
        long updatedAt
) {
}
