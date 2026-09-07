package com.phonepe.devx.arnold.models.comment;

import java.util.List;

public record CommentResponse(
        String id,
        String mergeRequestId,
        String discussionId,
        long noteId,
        Long draftId,
        Long authorId,
        String authorUsername,
        String body,
        String state,
        boolean resolvable,
        boolean resolved,
        boolean individualNote,
        boolean deleted,
        String source,
        long createdAt,
        long gitlabUpdatedAt,
        long lastSyncedAt,
        DiscussionPosition position,
        List<SuggestionResponse> suggestions
) {
}
