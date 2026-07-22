package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

public interface GitLabClient {
    OAuthToken exchangeAuthorizationCode(String code);

    OAuthToken refreshAccessToken(String refreshToken);

    GitLabUser currentUser(String accessToken);

    MergeRequestResponse resolveMergeRequest(String url, String accessToken);

    PublishedComment createDiscussion(long projectId, long mergeRequestIid, String body, String accessToken);

    record OAuthToken(String accessToken, String refreshToken, long expiresAt, String scope) {
    }

    record GitLabUser(long id, String username) {
    }
}
