package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;

import java.util.List;

public interface CommentService {
    List<CommentResponse> list(long projectId, long mrIid, String userId);

    List<CommentResponse> listDrafts(long projectId, long mrIid, String userId);

    CommentResponse create(CreateDiscussionRequest request, String userId);

    CommentResponse createDraft(CreateDiscussionRequest request, String userId);

    CommentResponse updateDraft(long draftId, UpdateDraftRequest request, String userId);

    void deleteDraft(long draftId, long projectId, long mrIid, String userId);

    List<CommentResponse> publishDrafts(PublishReviewRequest request, String userId);

    void resolveDiscussion(String discussionId, ResolveDiscussionRequest request, String userId);

    void applySuggestion(long suggestionId, String commitMessage, String userId);
}
