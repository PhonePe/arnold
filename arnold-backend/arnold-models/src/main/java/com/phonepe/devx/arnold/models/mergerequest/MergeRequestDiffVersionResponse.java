package com.phonepe.devx.arnold.models.mergerequest;

public record MergeRequestDiffVersionResponse(
        long id,
        String baseSha,
        String startSha,
        String headSha,
        long createdAt,
        String commitTitle
) {
}
