/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
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
    public MergeRequestResponse addChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                         final String username, final String userId) {
        return client.addReviewer(projectId, changeRequestIid, username,
                oauthService.validCredential(userId).accessToken());
    }

    @Override
    public MergeRequestResponse removeChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                            final long reviewerId, final String userId) {
        return client.removeReviewer(projectId, changeRequestIid, reviewerId,
                oauthService.validCredential(userId).accessToken());
    }

    @Override
    public List<ReviewerCandidateResponse> searchChangeRequestReviewers(final long projectId, final String query,
                                                                        final String userId) {
        return client.searchReviewers(projectId, query, oauthService.validCredential(userId).accessToken());
    }


    @Override
    public MergeRequestReviewResponse getChangeRequestReview(final long projectId, final long changeRequestIid,
                                                             final String userId) {
        return client.getMergeRequestReview(projectId, changeRequestIid, token(userId));
    }

    @Override
    public String getChangeRequestDiff(final long projectId, final long changeRequestIid, final String userId) {
        return client.getMergeRequestDiff(projectId, changeRequestIid,
                oauthService.validCredential(userId).accessToken());
    }

    @Override
    public String getChangeRequestDiffVersion(final long projectId, final long changeRequestIid, final long versionId,
                                              final String userId) {
        return client.getMergeRequestDiffVersion(projectId, changeRequestIid, versionId, token(userId));
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(final long projectId,
                                                                               final long changeRequestIid,
                                                                               final String userId) {
        return client.listMergeRequestDiffVersions(projectId, changeRequestIid, token(userId));
    }

    @Override
    public MergeRequestFilesResponse getChangeRequestFiles(final long projectId, final long changeRequestIid,
                                                           final String oldPath, final String newPath,
                                                           final String baseSha, final String headSha,
                                                           final String userId) {
        return client.getMergeRequestFiles(projectId, changeRequestIid, oldPath, newPath, baseSha, headSha,
                token(userId));
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        return client.createDiscussion(request,
                oauthService.validCredential(userId).accessToken());
    }

    @Override
    public List<PublishedComment> listDiscussions(final long projectId, final long changeRequestIid, final String userId) {
        return client.listDiscussions(projectId, changeRequestIid, token(userId));
    }

    @Override
    public List<PublishedComment> listDrafts(final long projectId, final long changeRequestIid, final String userId) {
        final var credential = oauthService.validCredential(userId);
        return client.listDrafts(projectId, changeRequestIid, credential.accessToken(), credential.providerUsername());
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest request, final String userId) {
        final var credential = oauthService.validCredential(userId);
        return client.createDraft(request, credential.accessToken(), credential.providerUsername());
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        final var credential = oauthService.validCredential(userId);
        return client.updateDraft(draftId, request, credential.accessToken(), credential.providerUsername());
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long changeRequestIid, final String userId) {
        client.deleteDraft(draftId, projectId, changeRequestIid, token(userId));
    }

    @Override
    public void publishDrafts(final PublishReviewRequest request, final String userId) {
        client.publishDrafts(request, token(userId));
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        client.resolveDiscussion(discussionId, request, token(userId));
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        client.applySuggestion(suggestionId, commitMessage, token(userId));
    }

    private String token(final String userId) {
        return oauthService.validCredential(userId).accessToken();
    }
}
