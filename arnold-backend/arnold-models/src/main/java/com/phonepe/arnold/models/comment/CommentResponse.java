package com.phonepe.arnold.models.comment;

import com.phonepe.arnold.models.request.AcceptanceStatus;

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
        AcceptanceStatus acceptanceStatus,
        String assessedSha,
        String assessmentReasoning,
        DiscussionPosition position,
        List<SuggestionResponse> suggestions
) {
}
