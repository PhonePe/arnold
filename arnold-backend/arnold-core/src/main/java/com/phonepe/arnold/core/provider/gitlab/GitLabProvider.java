/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.provider.gitlab;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.OAuthCredentialManager;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
        return withToken(userId, token -> client.resolveMergeRequest(url, token));
    }

    @Override
    public MergeRequestResponse addChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                         final String username, final String userId) {
        return withToken(userId, token -> client.addReviewer(projectId, changeRequestIid, username, token));
    }

    @Override
    public MergeRequestResponse removeChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                            final long reviewerId, final String userId) {
        return withToken(userId, token -> client.removeReviewer(projectId, changeRequestIid, reviewerId, token));
    }

    @Override
    public List<ReviewerCandidateResponse> searchChangeRequestReviewers(final long projectId, final String query,
                                                                        final String userId) {
        return withToken(userId, token -> client.searchReviewers(projectId, query, token));
    }


    @Override
    public MergeRequestReviewResponse getChangeRequestReview(final long projectId, final long changeRequestIid,
                                                             final String userId) {
        return withToken(userId, token -> client.getMergeRequestReview(projectId, changeRequestIid, token));
    }

    @Override
    public String getChangeRequestDiff(final long projectId, final long changeRequestIid, final String userId) {
        return withToken(userId, token -> client.getMergeRequestDiff(projectId, changeRequestIid, token));
    }

    @Override
    public String getChangeRequestDiffVersion(final long projectId, final long changeRequestIid, final long versionId,
                                              final String userId) {
        return withToken(userId, token -> client.getMergeRequestDiffVersion(projectId, changeRequestIid, versionId,
                token));
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(final long projectId,
                                                                               final long changeRequestIid,
                                                                               final String userId) {
        return withToken(userId, token -> client.listMergeRequestDiffVersions(projectId, changeRequestIid, token));
    }

    @Override
    public MergeRequestFilesResponse getChangeRequestFiles(final long projectId, final long changeRequestIid,
                                                           final String oldPath, final String newPath,
                                                           final String baseSha, final String headSha,
                                                           final String userId) {
        return withToken(userId, token -> client.getMergeRequestFiles(projectId, changeRequestIid, oldPath, newPath,
                baseSha, headSha, token));
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        return withToken(userId, token -> client.createDiscussion(request, token));
    }

    @Override
    public List<PublishedComment> listDiscussions(final long projectId, final long changeRequestIid, final String userId) {
        return withToken(userId, token -> client.listDiscussions(projectId, changeRequestIid, token));
    }

    @Override
    public List<PublishedComment> listDrafts(final long projectId, final long changeRequestIid, final String userId) {
        return withCredential(userId, credential -> client.listDrafts(projectId, changeRequestIid,
                credential.accessToken(), credential.providerUsername()));
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest request, final String userId) {
        return withCredential(userId, credential -> client.createDraft(request, credential.accessToken(),
                credential.providerUsername()));
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        return withCredential(userId, credential -> client.updateDraft(draftId, request, credential.accessToken(),
                credential.providerUsername()));
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long changeRequestIid, final String userId) {
        runWithToken(userId, token -> client.deleteDraft(draftId, projectId, changeRequestIid, token));
    }

    @Override
    public void publishDrafts(final PublishReviewRequest request, final String userId) {
        runWithToken(userId, token -> client.publishDrafts(request, token));
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        runWithToken(userId, token -> client.resolveDiscussion(discussionId, request, token));
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        runWithToken(userId, token -> client.applySuggestion(suggestionId, commitMessage, token));
    }

    private <T> T withToken(final String userId, final Function<String, T> call) {
        return withCredential(userId, credential -> call.apply(credential.accessToken()));
    }

    private void runWithToken(final String userId, final Consumer<String> call) {
        withToken(userId, token -> {
            call.accept(token);
            return null;
        });
    }

    /**
     * Runs a GitLab call with the user's token; a 401 marks the credential for reconnect unless the token was
     * rotated concurrently, in which case the call is retried once with the newer token.
     */
    private <T> T withCredential(final String userId, final Function<VersionControlCredential, T> call) {
        var credential = oauthService.validCredential(userId);
        try {
            return call.apply(credential);
        } catch (ArnoldError error) {
            if (!OAuthCredentialManager.unauthorized(error)) {
                throw error;
            }
            credential = oauthService.unauthorized(userId, credential);
        }
        try {
            return call.apply(credential);
        } catch (ArnoldError error) {
            if (OAuthCredentialManager.unauthorized(error)) {
                oauthService.unauthorized(userId, credential);
            }
            throw error;
        }
    }
}
