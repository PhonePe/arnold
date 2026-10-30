package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerCandidateResponse;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class UnavailableGitLabProvider implements VersionControlProvider {
    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITLAB;
    }

    @Override
    public MergeRequestResponse resolveChangeRequest(final String url, final String userId) {
        throw unavailable();
    }

    @Override
    public MergeRequestResponse addChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                         final String username, final String userId) {
        throw unavailable();
    }

    @Override
    public MergeRequestResponse removeChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                            final long reviewerId, final String userId) {
        throw unavailable();
    }

    @Override
    public List<ReviewerCandidateResponse> searchChangeRequestReviewers(final long projectId, final String query,
                                                                        final String userId) {
        throw unavailable();
    }

    @Override
    public void triggerSmartDiff(final String mergeRequestUrl, final long projectId, final long changeRequestIid,
                                 final String headSha, final String executionId) {
        throw unavailable();
    }

    @Override
    public MergeRequestReviewResponse getChangeRequestReview(final long projectId, final long changeRequestIid,
                                                             final String userId) {
        throw unavailable();
    }

    @Override
    public String getChangeRequestDiff(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public String getChangeRequestDiffVersion(final long projectId, final long changeRequestIid, final long versionId,
                                              final String userId) {
        throw unavailable();
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(final long projectId,
                                                                               final long changeRequestIid,
                                                                               final String userId) {
        throw unavailable();
    }

    @Override
    public MergeRequestFilesResponse getChangeRequestFiles(final long projectId, final long changeRequestIid,
                                                           final String oldPath, final String newPath,
                                                           final String baseSha, final String headSha,
                                                           final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public List<PublishedComment> listDiscussions(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public List<PublishedComment> listDrafts(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public void publishDrafts(final PublishReviewRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        throw unavailable();
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        throw unavailable();
    }

    private RuntimeException unavailable() {
        return ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                .message("GitLab provider is not configured")
                .build();
    }
}
