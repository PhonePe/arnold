package com.phonepe.devx.arnold.models.es;

import java.util.List;

public record MergeRequestDocument(
        String id,
        long projectId,
        long gitlabId,
        long iid,
        String projectPath,
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
        long updatedAt
) {
}
