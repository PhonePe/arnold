package com.phonepe.devx.arnold.core.model.vcs;

import com.phonepe.devx.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import com.phonepe.devx.arnold.models.comment.SuggestionResponse;

import java.util.List;

public record PublishedComment(String providerId, long numericId, Long draftId, String threadId, Long authorId,
                               String authorUsername, String body, boolean resolvable, boolean resolved,
                               boolean individualNote, String state, long createdAt, long updatedAt,
                               DiscussionPosition position, List<SuggestionResponse> suggestions,
                               PersonaAttribution persona) {
    public PublishedComment(final String providerId, final long numericId, final Long draftId, final String threadId,
                            final Long authorId, final String authorUsername, final String body,
                            final boolean resolvable, final boolean resolved, final boolean individualNote,
                            final String state, final long createdAt, final long updatedAt,
                            final DiscussionPosition position, final List<SuggestionResponse> suggestions) {
        this(providerId, numericId, draftId, threadId, authorId, authorUsername, body, resolvable, resolved,
                individualNote, state, createdAt, updatedAt, position, suggestions, null);
    }
}
