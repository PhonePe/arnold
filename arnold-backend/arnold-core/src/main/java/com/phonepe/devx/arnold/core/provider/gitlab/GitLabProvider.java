package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GitLabProvider implements VersionControlProvider {
    private final GitLabClient client;
    private final GitLabOAuthService oauthService;

    @Inject
    public GitLabProvider(final GitLabClient client, final GitLabOAuthService oauthService) {
        this.client = client;
        this.oauthService = oauthService;
    }

    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITLAB;
    }

    @Override
    public MergeRequestResponse resolveChangeRequest(final String url, final String userId) {
        return client.resolveMergeRequest(url, oauthService.validCredential(userId).accessToken());
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        return client.createDiscussion(request.getProjectId(), request.getMrIid(), request.getBody(),
                oauthService.validCredential(userId).accessToken());
    }
}
