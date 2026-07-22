package com.phonepe.devx.arnold.core.model.comment;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;

public record CommentRecord(
        String id,
        VersionControlProviderType provider,
        String changeRequestId,
        String discussionId,
        long noteId,
        Long authorId,
        String body,
        String state,
        boolean resolved,
        boolean deleted,
        String source,
        long providerUpdatedAt,
        long lastSyncedAt
) implements Id {

    public static String key(final VersionControlProviderType provider, final String repositoryId,
                             final long changeNumber, final long commentId) {
        return provider.name().toLowerCase() + ":" + repositoryId + ":" + changeNumber + ":" + commentId;
    }
}
