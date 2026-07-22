package com.phonepe.devx.arnold.models.comment;

public record CommentResponse(
        String id,
        String mergeRequestId,
        String discussionId,
        long noteId,
        Long authorId,
        String body,
        String state,
        boolean resolved,
        boolean deleted,
        String source,
        long gitlabUpdatedAt,
        long lastSyncedAt
) {
}
