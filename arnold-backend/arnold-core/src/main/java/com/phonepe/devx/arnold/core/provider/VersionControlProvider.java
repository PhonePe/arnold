package com.phonepe.devx.arnold.core.provider;

import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

public interface VersionControlProvider {
    VersionControlProviderType type();

    MergeRequestResponse resolveChangeRequest(String url, String userId);

    PublishedComment createDiscussion(CreateDiscussionRequest request, String userId);
}
