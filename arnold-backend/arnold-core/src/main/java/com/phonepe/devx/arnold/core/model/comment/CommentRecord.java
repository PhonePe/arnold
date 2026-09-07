package com.phonepe.devx.arnold.core.model.comment;

import com.livetheoogway.crudstore.core.Id;
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
        List<SuggestionResponse> suggestions
) implements Id {

    public static String key(final VersionControlProviderType provider, final String repositoryId,
                             final long changeNumber, final long commentId) {
        return provider.name().toLowerCase() + ":" + repositoryId + ":" + changeNumber + ":" + commentId;
    }
}
