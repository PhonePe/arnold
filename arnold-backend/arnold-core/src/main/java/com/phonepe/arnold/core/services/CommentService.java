package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.comment.CommentResponse;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;

import java.util.List;

public interface CommentService {
    List<CommentResponse> list(VersionControlProviderType provider, long projectId, long mrIid, String userId);

    List<CommentResponse> listDrafts(VersionControlProviderType provider, long projectId, long mrIid, String userId);

    CommentResponse create(CreateDiscussionRequest request, String userId);

    CommentResponse createDraft(CreateDiscussionRequest request, String userId);

    CommentResponse updateDraft(long draftId, UpdateDraftRequest request, String userId);

    void deleteDraft(VersionControlProviderType provider, long draftId, long projectId, long mrIid, String userId);

    List<CommentResponse> publishDrafts(PublishReviewRequest request, String userId);

    void resolveDiscussion(String discussionId, ResolveDiscussionRequest request, String userId);

    void applySuggestion(VersionControlProviderType provider, long suggestionId, String commitMessage, String userId);
}
