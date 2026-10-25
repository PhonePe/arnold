package com.phonepe.arnold.core.provider;

import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;

import java.util.List;

public interface VersionControlProvider {
    VersionControlProviderType type();

    MergeRequestResponse resolveChangeRequest(String url, String userId);

    MergeRequestResponse addChangeRequestReviewer(long projectId, long changeRequestIid, String username,
                                                   String userId);

    MergeRequestResponse removeChangeRequestReviewer(long projectId, long changeRequestIid, long reviewerId,
                                                      String userId);

    List<ReviewerCandidateResponse> searchChangeRequestReviewers(long projectId, String query, String userId);


    MergeRequestReviewResponse getChangeRequestReview(long projectId, long changeRequestIid, String userId);

    String getChangeRequestDiff(long projectId, long changeRequestIid, String userId);

    String getChangeRequestDiffVersion(long projectId, long changeRequestIid, long versionId, String userId);

    List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(long projectId, long changeRequestIid,
                                                                        String userId);

    MergeRequestFilesResponse getChangeRequestFiles(long projectId, long changeRequestIid, String oldPath,
                                                    String newPath, String baseSha, String headSha, String userId);

    PublishedComment createDiscussion(CreateDiscussionRequest request, String userId);

    List<PublishedComment> listDiscussions(long projectId, long changeRequestIid, String userId);

    List<PublishedComment> listDrafts(long projectId, long changeRequestIid, String userId);

    PublishedComment createDraft(CreateDiscussionRequest request, String userId);

    PublishedComment updateDraft(long draftId, UpdateDraftRequest request, String userId);

    void deleteDraft(long draftId, long projectId, long changeRequestIid, String userId);

    void publishDrafts(PublishReviewRequest request, String userId);

    void resolveDiscussion(String discussionId, ResolveDiscussionRequest request, String userId);

    void applySuggestion(long suggestionId, String commitMessage, String userId);
}
