package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;

import java.util.List;

public interface CommentService {
    List<CommentResponse> list(long projectId, long mrIid, String userId);

    CommentResponse create(CreateDiscussionRequest request, String userId);
}
