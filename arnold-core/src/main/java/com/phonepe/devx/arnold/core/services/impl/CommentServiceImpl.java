package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;

@Singleton
public class CommentServiceImpl implements CommentService {
    private final AerospikeCommentStore commentStore;
    private final VersionControlProviders providers;

    @Inject
    public CommentServiceImpl(final AerospikeCommentStore commentStore,
                              final VersionControlProviders providers) {
        this.commentStore = commentStore;
        this.providers = providers;
    }

    @Override
    public List<CommentResponse> list(final long projectId, final long mrIid, final String userId) {
        final var mergeRequestId = com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord.key(
                VersionControlProviderType.GITLAB, Long.toString(projectId), mrIid);
        return commentStore.list().stream()
                .filter(comment -> mergeRequestId.equals(comment.changeRequestId()))
                .filter(comment -> !comment.deleted())
                .sorted(Comparator.comparingLong(CommentRecord::providerUpdatedAt))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CommentResponse create(final CreateDiscussionRequest request, final String userId) {
        final var pendingId = "operation:" + request.getClientOperationId();
        final var providerType = VersionControlProviderType.valueOf(request.getProvider().name());
        final var changeRequestId = com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord.key(
                providerType, Long.toString(request.getProjectId()), request.getMrIid());
        final var existing = commentStore.get(pendingId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        final var now = System.currentTimeMillis();
        final var pending = new CommentRecord(pendingId, providerType, changeRequestId, null, 0,
                null, request.getBody(), "pending", false, false, "arnold_user", now, now);
        commentStore.create(pending);
        try {
            final var published = providers.get(providerType).createDiscussion(request, userId);
            final var canonicalId = CommentRecord.key(providerType, Long.toString(request.getProjectId()),
                    request.getMrIid(), published.numericId());
            final var completed = new CommentRecord(canonicalId, providerType, pending.changeRequestId(),
                    published.threadId(), published.numericId(), published.authorId(), pending.body(), "published", false, false,
                    pending.source(), published.updatedAt(), System.currentTimeMillis());
            commentStore.create(completed);
            commentStore.delete(pendingId);
            return toResponse(completed);
        } catch (RuntimeException failure) {
            commentStore.update(new CommentRecord(pending.id(), providerType, pending.changeRequestId(), null, 0, null, pending.body(),
                    "unknown", false, false, pending.source(), now, System.currentTimeMillis()));
            throw failure;
        }
    }

    private CommentResponse toResponse(final CommentRecord comment) {
        return new CommentResponse(comment.id(), comment.changeRequestId(), comment.discussionId(), comment.noteId(),
                comment.authorId(), comment.body(), comment.state(), comment.resolved(), comment.deleted(),
                comment.source(), comment.providerUpdatedAt(), comment.lastSyncedAt());
    }
}
