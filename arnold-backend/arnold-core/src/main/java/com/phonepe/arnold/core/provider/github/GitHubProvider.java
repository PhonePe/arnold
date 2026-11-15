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

package com.phonepe.arnold.core.provider.github;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;

/**
 * Pull request review operations on GitHub, performed as the signed-in user through the GitHub App.
 */
@Singleton
public class GitHubProvider implements VersionControlProvider {
    private final GitHubIntegration integration;

    @Inject
    public GitHubProvider(final GitHubIntegration integration) {
        this.integration = integration;
    }

    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITHUB;
    }

    @Override
    public MergeRequestResponse resolveChangeRequest(final String url, final String userId) {
        return withToken(userId, token -> integration.client().resolvePullRequest(url, token));
    }

    @Override
    public MergeRequestResponse addChangeRequestReviewer(final long repositoryId, final long number,
                                                         final String username, final String userId) {
        return withToken(userId, token -> integration.client().addReviewer(repositoryId, number, username, token));
    }

    @Override
    public MergeRequestResponse removeChangeRequestReviewer(final long repositoryId, final long number,
                                                            final long reviewerId, final String userId) {
        return withToken(userId, token -> integration.client().removeReviewer(repositoryId, number, reviewerId,
                token));
    }

    @Override
    public List<ReviewerCandidateResponse> searchChangeRequestReviewers(final long repositoryId, final String query,
                                                                        final String userId) {
        return withToken(userId, token -> integration.client().searchReviewers(repositoryId, query, token));
    }

    @Override
    public MergeRequestReviewResponse getChangeRequestReview(final long repositoryId, final long number,
                                                             final String userId) {
        return withToken(userId, token -> integration.client().review(repositoryId, number, token));
    }

    @Override
    public String getChangeRequestDiff(final long repositoryId, final long number, final String userId) {
        return withToken(userId, token -> integration.client().diff(repositoryId, number, token));
    }

    @Override
    public String getChangeRequestDiffVersion(final long repositoryId, final long number, final long versionId,
                                              final String userId) {
        return withToken(userId, token -> integration.client().diffVersion(repositoryId, number, versionId, token));
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(final long repositoryId,
                                                                               final long number,
                                                                               final String userId) {
        return withToken(userId, token -> integration.client().diffVersions(repositoryId, number, token));
    }

    @Override
    public MergeRequestFilesResponse getChangeRequestFiles(final long repositoryId, final long number,
                                                           final String oldPath, final String newPath,
                                                           final String baseSha, final String headSha,
                                                           final String userId) {
        return withToken(userId, token -> integration.client().files(repositoryId, number, oldPath, newPath, baseSha,
                headSha, token));
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        return withToken(userId, token -> integration.client().createDiscussion(request, token));
    }

    @Override
    public List<PublishedComment> listDiscussions(final long repositoryId, final long number, final String userId) {
        return withToken(userId, token -> integration.client().discussions(repositoryId, number, token));
    }

    @Override
    public List<PublishedComment> listDrafts(final long repositoryId, final long number, final String userId) {
        return integration.withUserToken(userId, credential -> integration.client().drafts(repositoryId, number,
                credential.accessToken(), credential.providerUsername()));
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest request, final String userId) {
        return integration.withUserToken(userId, credential -> integration.client().createDraft(request,
                credential.accessToken(), credential.providerUsername()));
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        return withToken(userId, token -> integration.client().updateDraft(request.getProjectId(), draftId,
                request.getBody(), token));
    }

    @Override
    public void deleteDraft(final long draftId, final long repositoryId, final long number, final String userId) {
        withToken(userId, token -> {
            integration.client().deleteDraft(repositoryId, draftId, token);
            return null;
        });
    }

    @Override
    public void publishDrafts(final PublishReviewRequest request, final String userId) {
        integration.withUserToken(userId, credential -> {
            integration.client().publishReview(request, credential.accessToken(), credential.providerUsername());
            return null;
        });
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        withToken(userId, token -> {
            integration.client().resolveThread(discussionId, request.isResolved(), token);
            return null;
        });
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED)
                .message("GitHub does not support applying suggestions through its API").build();
    }

    private <T> T withToken(final String userId, final Function<String, T> call) {
        return integration.withUserToken(userId, credential -> call.apply(credential.accessToken()));
    }
}
