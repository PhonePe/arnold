package com.phonepe.devx.arnold.models.es;

import java.util.List;

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
        List<String> usefulAreas,
        List<String> notUsefulAreas,
        long updatedAt
) {
}
