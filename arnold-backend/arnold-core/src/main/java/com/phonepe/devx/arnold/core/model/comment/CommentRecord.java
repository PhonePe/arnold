package com.phonepe.devx.arnold.core.model.comment;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import com.phonepe.devx.arnold.models.comment.SuggestionResponse;

import java.util.List;

public record CommentRecord(
        String id,
        VersionControlProviderType provider,
        String changeRequestId,
        String discussionId,
        long noteId,
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
        long providerUpdatedAt,
        long lastSyncedAt,
        DiscussionPosition position,
        List<SuggestionResponse> suggestions,
        PersonaAttribution persona
) implements Id {

    public CommentRecord(final String id, final VersionControlProviderType provider, final String changeRequestId,
                         final String discussionId, final long noteId, final Long authorId,
                         final String authorUsername, final String body, final String state,
                         final boolean resolvable, final boolean resolved, final boolean individualNote,
                         final boolean deleted, final String source, final long createdAt,
                         final long providerUpdatedAt, final long lastSyncedAt, final DiscussionPosition position,
                         final List<SuggestionResponse> suggestions) {
        this(id, provider, changeRequestId, discussionId, noteId, authorId, authorUsername, body, state, resolvable,
                resolved, individualNote, deleted, source, createdAt, providerUpdatedAt, lastSyncedAt, position,
                suggestions, null);
    }

    public static String key(final VersionControlProviderType provider, final String repositoryId,
                             final long changeNumber, final long commentId) {
        return provider.name().toLowerCase() + ":" + repositoryId + ":" + changeNumber + ":" + commentId;
    }
}
