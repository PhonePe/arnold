package com.phonepe.devx.arnold.models.mergerequest;

import java.util.List;

public record MergeRequestResponse(
        String id,
        long projectId,
        long gitlabId,
        long iid,
        String projectPath,
        String title,
        String description,
        String webUrl,
        String sourceBranch,
        String targetBranch,
        String headSha,
        String state,
        String mergeStatus,
        boolean draft,
        Long authorId,
        List<Long> assigneeIds,
        List<Long> reviewerIds,
        List<String> labels,
        long gitlabUpdatedAt,
        long lastSyncedAt
) {
}
