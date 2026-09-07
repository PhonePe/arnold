package com.phonepe.devx.arnold.core.model.vcs;

import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import com.phonepe.devx.arnold.models.comment.SuggestionResponse;

import java.util.List;

public record PublishedComment(String providerId, long numericId, Long draftId, String threadId, Long authorId,
                               String authorUsername, String body, boolean resolvable, boolean resolved,
                               boolean individualNote, String state, long createdAt, long updatedAt,
                               DiscussionPosition position, List<SuggestionResponse> suggestions) {
}
