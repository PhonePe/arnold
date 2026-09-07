package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

@Singleton
public class CommentServiceImpl implements CommentService {
    private final AerospikeCommentStore commentStore;
    private final VersionControlProviders providers;
    private final VersionControlSyncService syncService;

    @Inject
    public CommentServiceImpl(final AerospikeCommentStore commentStore,
                              final VersionControlProviders providers,
                              final VersionControlSyncService syncService) {
        this.commentStore = commentStore;
        this.providers = providers;
        this.syncService = syncService;
    }

    @Override
    public List<CommentResponse> list(final long projectId, final long mrIid, final String userId) {
        final var provider = providers.get(VersionControlProviderType.GITLAB);
        final var mergeRequestId = changeRequestId(projectId, mrIid);
        final var published = provider.listDiscussions(projectId, mrIid, userId);
        published.stream().map(comment -> toRecord(comment, projectId, mrIid)).forEach(syncService::synchronize);
        reconcileDeleted(mergeRequestId, published);
        final var drafts = provider.listDrafts(projectId, mrIid, userId);
        return Stream.concat(published.stream(), drafts.stream())
                .sorted(Comparator.comparingLong(PublishedComment::createdAt))
                .map(comment -> toResponse(comment, mergeRequestId))
                .toList();
    }

    @Override
    public List<CommentResponse> listDrafts(final long projectId, final long mrIid, final String userId) {
        final var mergeRequestId = changeRequestId(projectId, mrIid);
        return providers.get(VersionControlProviderType.GITLAB).listDrafts(projectId, mrIid, userId).stream()
                .map(comment -> toResponse(comment, mergeRequestId))
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
                null, null, request.getBody(), "pending", false, false,
                request.getDiscussionId() == null && request.getPosition() == null, false, "arnold_user", now, now,
                now, request.getPosition(), List.of());
        commentStore.create(pending);
        try {
            final var published = providers.get(providerType).createDiscussion(request, userId);
            final var canonicalId = CommentRecord.key(providerType, Long.toString(request.getProjectId()),
                    request.getMrIid(), published.numericId());
            final var completed = new CommentRecord(canonicalId, providerType, pending.changeRequestId(),
                    published.threadId(), published.numericId(), published.authorId(), published.authorUsername(),
                    published.body(), "published", published.resolvable(), published.resolved(),
                    published.individualNote(), false, pending.source(), published.createdAt(), published.updatedAt(),
                    System.currentTimeMillis(), published.position(), suggestions(published));
            commentStore.create(completed);
            commentStore.delete(pendingId);
            return toResponse(completed);
        } catch (RuntimeException failure) {
            commentStore.update(new CommentRecord(pending.id(), providerType, pending.changeRequestId(), null, 0, null,
                    null, pending.body(), "unknown", false, false, pending.individualNote(), false, pending.source(),
                    now, now, System.currentTimeMillis(), pending.position(), List.of()));
            throw failure;
        }
    }

    @Override
    public CommentResponse createDraft(final CreateDiscussionRequest request, final String userId) {
        final var provider = providers.get(VersionControlProviderType.valueOf(request.getProvider().name()));
        return toResponse(provider.createDraft(request, userId), changeRequestId(request.getProjectId(), request.getMrIid()));
    }

    @Override
    public CommentResponse updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        return toResponse(providers.get(VersionControlProviderType.GITLAB).updateDraft(draftId, request, userId),
                changeRequestId(request.getProjectId(), request.getMrIid()));
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long mrIid, final String userId) {
        providers.get(VersionControlProviderType.GITLAB).deleteDraft(draftId, projectId, mrIid, userId);
    }

    @Override
    public List<CommentResponse> publishDrafts(final PublishReviewRequest request, final String userId) {
        providers.get(VersionControlProviderType.GITLAB).publishDrafts(request, userId);
        return list(request.getProjectId(), request.getMrIid(), userId);
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        providers.get(VersionControlProviderType.GITLAB).resolveDiscussion(discussionId, request, userId);
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        providers.get(VersionControlProviderType.GITLAB).applySuggestion(suggestionId, commitMessage, userId);
    }

    private void reconcileDeleted(final String mergeRequestId, final List<PublishedComment> published) {
        final var present = new HashSet<>(published.stream().map(PublishedComment::numericId).toList());
        final var now = System.currentTimeMillis();
        commentStore.list().stream()
                .filter(comment -> mergeRequestId.equals(comment.changeRequestId()))
                .filter(comment -> "gitlab".equals(comment.source()) && !comment.deleted())
                .filter(comment -> !present.contains(comment.noteId()))
                .map(comment -> new CommentRecord(comment.id(), comment.provider(), comment.changeRequestId(),
                        comment.discussionId(), comment.noteId(), comment.authorId(), comment.authorUsername(), comment.body(),
                        comment.state(), comment.resolvable(), comment.resolved(), comment.individualNote(), true,
                        comment.source(), comment.createdAt(), now, now, comment.position(), comment.suggestions()))
                .forEach(syncService::synchronize);
    }

    private CommentRecord toRecord(final PublishedComment comment, final long projectId, final long mrIid) {
        return new CommentRecord(CommentRecord.key(VersionControlProviderType.GITLAB, Long.toString(projectId), mrIid,
                comment.numericId()), VersionControlProviderType.GITLAB, changeRequestId(projectId, mrIid),
                comment.threadId(), comment.numericId(), comment.authorId(), comment.authorUsername(), comment.body(),
                comment.state(), comment.resolvable(), comment.resolved(), comment.individualNote(), false, "gitlab",
                comment.createdAt(), comment.updatedAt(), System.currentTimeMillis(), comment.position(),
                suggestions(comment));
    }

    private String changeRequestId(final long projectId, final long mrIid) {
        return com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord.key(
                VersionControlProviderType.GITLAB, Long.toString(projectId), mrIid);
    }

    private CommentResponse toResponse(final PublishedComment comment, final String mergeRequestId) {
        return new CommentResponse(comment.providerId(), mergeRequestId, comment.threadId(), comment.numericId(),
                comment.draftId(), comment.authorId(), comment.authorUsername(), comment.body(), comment.state(),
                comment.resolvable(), comment.resolved(), comment.individualNote(), false, "gitlab", comment.createdAt(),
                comment.updatedAt(), System.currentTimeMillis(), comment.position(), suggestions(comment));
    }

    private CommentResponse toResponse(final CommentRecord comment) {
        return new CommentResponse(comment.id(), comment.changeRequestId(), comment.discussionId(), comment.noteId(),
                null, comment.authorId(), comment.authorUsername(), comment.body(), comment.state(), comment.resolvable(),
                comment.resolved(), comment.individualNote(), comment.deleted(), comment.source(), comment.createdAt(),
                comment.providerUpdatedAt(), comment.lastSyncedAt(), comment.position(),
                comment.suggestions() == null ? List.of() : comment.suggestions());
    }

    private List<com.phonepe.devx.arnold.models.comment.SuggestionResponse> suggestions(
            final PublishedComment comment) {
        return comment.suggestions() == null ? List.of() : comment.suggestions();
    }
}
