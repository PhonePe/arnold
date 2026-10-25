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

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;

import javax.inject.Singleton;
import java.util.List;

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
    public MergeRequestResponse addChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                         final String username, final String userId) {
        throw unavailable();
    }

    @Override
    public MergeRequestResponse removeChangeRequestReviewer(final long projectId, final long changeRequestIid,
                                                            final long reviewerId, final String userId) {
        throw unavailable();
    }

    @Override
    public List<ReviewerCandidateResponse> searchChangeRequestReviewers(final long projectId, final String query,
                                                                        final String userId) {
        throw unavailable();
    }


    @Override
    public MergeRequestReviewResponse getChangeRequestReview(final long projectId, final long changeRequestIid,
                                                             final String userId) {
        throw unavailable();
    }

    @Override
    public String getChangeRequestDiff(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public String getChangeRequestDiffVersion(final long projectId, final long changeRequestIid, final long versionId,
                                              final String userId) {
        throw unavailable();
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listChangeRequestDiffVersions(final long projectId,
                                                                               final long changeRequestIid,
                                                                               final String userId) {
        throw unavailable();
    }

    @Override
    public MergeRequestFilesResponse getChangeRequestFiles(final long projectId, final long changeRequestIid,
                                                           final String oldPath, final String newPath,
                                                           final String baseSha, final String headSha,
                                                           final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public List<PublishedComment> listDiscussions(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public List<PublishedComment> listDrafts(final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long changeRequestIid, final String userId) {
        throw unavailable();
    }

    @Override
    public void publishDrafts(final PublishReviewRequest request, final String userId) {
        throw unavailable();
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        throw unavailable();
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String userId) {
        throw unavailable();
    }

    private RuntimeException unavailable() {
        return ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                .message("GitLab provider is not configured")
                .build();
    }
}
