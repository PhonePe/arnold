package com.phonepe.devx.arnold.models.es;

import java.util.List;

public record MergeRequestDocument(
        String id,
        String provider,
        String repositoryId,
        String providerId,
        long number,
        String repositoryPath,
        String title,
        String description,
        String webUrl,
        String state,
        String mergeStatus,
        boolean draft,
        Long authorId,
        List<Long> assigneeIds,
        List<Long> reviewerIds,
        List<String> labels,
        long createdAt,
        long mergedAt,
        long closedAt,
        long mergeDurationMs,
        boolean hasConflicts,
        boolean blockingDiscussionsResolved,
        long providerUpdatedAt,
        long lastSyncedAt,
        boolean projectArchived,
        String sourceBranch,
        String targetBranch,
        String headSha,
        String authorName,
        List<String> reviewerNames,
        String reviewState,
        boolean arnoldReviewed,
        String latestAnalysisRunId,
        String latestAnalysisOutcome
) {
}
