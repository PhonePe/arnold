package com.phonepe.devx.arnold.models.comment;

import com.phonepe.devx.arnold.models.request.AcceptanceStatus;

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
        List<SuggestionResponse> suggestions,
        String analysisRunId,
        String personaId,
        Long personaVersion,
        String personaName,
        String reviewSource
) {
    /** Persona-attributed AI comment. */
    public static final String SOURCE_PERSONA = "persona";
    /** AI comment from the general review pass (no persona). */
    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_HUMAN = "human";
}
