/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.model.comment;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.comment.DiscussionPosition;
import com.phonepe.arnold.models.comment.SuggestionResponse;

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
