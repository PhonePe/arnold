package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

import java.util.List;

public interface GitLabClient {
    OAuthToken exchangeAuthorizationCode(String code);

    OAuthToken refreshAccessToken(String refreshToken);

    GitLabUser currentUser(String accessToken);

    MergeRequestResponse resolveMergeRequest(String url, String accessToken);

    ChangeRequestRecord getMergeRequest(long projectId, long mergeRequestIid, String accessToken);

    List<ChangeRequestRecord> listMergeRequests(long providerUserId, MergeRequestRole role, String state,
                                                Long updatedAfter, String accessToken);

    PublishedComment createDiscussion(long projectId, long mergeRequestIid, String body, String accessToken);

    record OAuthToken(String accessToken, String refreshToken, long expiresAt, String scope) {
    }

    record GitLabUser(long id, String username) {
    }
}
