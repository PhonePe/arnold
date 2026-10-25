package com.phonepe.arnold.core.provider.gitlab;

import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
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

public interface GitLabClient {
    OAuthToken exchangeAuthorizationCode(String code);

    OAuthToken refreshAccessToken(String refreshToken);

    GitLabUser currentUser(String accessToken);

    MergeRequestResponse resolveMergeRequest(String url, String accessToken);

    MergeRequestResponse addReviewer(long projectId, long mergeRequestIid, String username, String accessToken);

    MergeRequestResponse removeReviewer(long projectId, long mergeRequestIid, long reviewerId, String accessToken);

    List<ReviewerCandidateResponse> searchReviewers(long projectId, String query, String accessToken);

    void triggerSmartDiff(String mergeRequestUrl, long projectId, long mergeRequestIid, String headSha,
                          String executionId);

    ChangeRequestRecord getMergeRequest(long projectId, long mergeRequestIid, String accessToken);

    boolean isProjectArchived(long projectId, String accessToken);

    List<ChangeRequestRecord> listMergeRequests(long providerUserId, MergeRequestRole role, String state,
                                                Long updatedAfter, String accessToken);

    PublishedComment createDiscussion(long projectId, long mergeRequestIid, String body, String accessToken);

    MergeRequestReviewResponse getMergeRequestReview(long projectId, long mergeRequestIid, String accessToken);

    String getMergeRequestDiff(long projectId, long mergeRequestIid, String accessToken);

    String getMergeRequestDiffVersion(long projectId, long mergeRequestIid, long versionId, String accessToken);

    List<MergeRequestDiffVersionResponse> listMergeRequestDiffVersions(long projectId, long mergeRequestIid,
                                                                       String accessToken);

    MergeRequestFilesResponse getMergeRequestFiles(long projectId, long mergeRequestIid, String oldPath,
                                                   String newPath, String baseSha, String headSha, String accessToken);

    PublishedComment createDiscussion(CreateDiscussionRequest request, String accessToken);

    List<PublishedComment> listDiscussions(long projectId, long mergeRequestIid, String accessToken);

    List<PublishedComment> listDrafts(long projectId, long mergeRequestIid, String accessToken, String authorUsername);

    PublishedComment createDraft(CreateDiscussionRequest request, String accessToken, String authorUsername);

    PublishedComment updateDraft(long draftId, UpdateDraftRequest request, String accessToken, String authorUsername);

    void deleteDraft(long draftId, long projectId, long mergeRequestIid, String accessToken);

    void publishDrafts(PublishReviewRequest request, String accessToken);

    void resolveDiscussion(String discussionId, ResolveDiscussionRequest request, String accessToken);

    void applySuggestion(long suggestionId, String commitMessage, String accessToken);

    record OAuthToken(String accessToken, String refreshToken, long expiresAt, String scope) {
    }

    record GitLabUser(long id, String username) {
    }
}
