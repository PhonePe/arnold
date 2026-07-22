package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

import javax.inject.Singleton;

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
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        throw unavailable();
    }

    private RuntimeException unavailable() {
        return ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                .message("GitLab provider is not configured")
                .build();
    }
}
