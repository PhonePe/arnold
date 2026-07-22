package com.phonepe.devx.arnold.models.es;

public record CommentDocument(
        String id,
        String mergeRequestId,
        String discussionId,
        long noteId,
        Long authorId,
        String body,
        boolean resolved,
        boolean deleted,
        String source,
        long updatedAt
) {
}
