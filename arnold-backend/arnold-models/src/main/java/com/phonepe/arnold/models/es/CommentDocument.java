package com.phonepe.arnold.models.es;

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
        long updatedAt,
        long createdAt,
        String authorUsername,
        String state,
        boolean resolvable,
        boolean individualNote,
        Long mergeRequestAuthorId,
        boolean byMergeRequestAuthor
) {
}
