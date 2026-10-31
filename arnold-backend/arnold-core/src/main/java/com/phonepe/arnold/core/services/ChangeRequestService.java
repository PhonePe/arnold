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

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.arnold.models.mergerequest.ViewedFilesResponse;
import com.phonepe.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.arnold.models.mergerequest.SmartDiffResultRequest;

import java.util.List;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ChangeRequestService {
    MergeRequestResponse get(VersionControlProviderType provider, String repositoryId, long number, String userId);

    MergeRequestResponse resolve(VersionControlProviderType provider, String url, String userId);

    MergeRequestResponse addReviewer(VersionControlProviderType provider, long projectId, long iid, String username, String userId);

    MergeRequestResponse removeReviewer(VersionControlProviderType provider, long projectId, long iid, long reviewerId, String userId);

    List<ReviewerCandidateResponse> searchReviewers(VersionControlProviderType provider, long projectId, String query, String userId);

    MergeRequestReviewResponse getReview(VersionControlProviderType provider, long projectId, long iid, String userId);

    String getDiff(VersionControlProviderType provider, long projectId, long iid, String userId);

    String getDiffVersion(VersionControlProviderType provider, long projectId, long iid, long versionId, String userId);

    List<MergeRequestDiffVersionResponse> listDiffVersions(VersionControlProviderType provider, long projectId, long iid, String userId);

    MergeRequestFilesResponse getFiles(VersionControlProviderType provider, long projectId, long iid, String oldPath, String newPath, String baseSha,
                                       String headSha, String userId);

    ViewedFilesResponse getViewedFiles(VersionControlProviderType provider, long projectId, long iid, String headSha, String userId);

    ViewedFilesResponse updateViewedFiles(VersionControlProviderType provider, long projectId, long iid, ViewedFilesRequest request, String userId);

    SmartDiffResponse getSmartDiff(VersionControlProviderType provider, long projectId, long iid, String headSha);

    SmartDiffResponse requestSmartDiff(VersionControlProviderType provider, long projectId, long iid, SmartDiffRequest request, String userId);

    SmartDiffResponse updateSmartDiff(VersionControlProviderType provider, long projectId, long iid, SmartDiffResultRequest request);

    boolean synchronize(ChangeRequestRecord mergeRequest);

    Optional<ChangeRequestRecord> record(String mergeRequestId);

    Map<String, ChangeRequestRecord> records(Collection<String> mergeRequestIds);

    /**
     * Full scan of stored change requests; callers must bound what they keep in memory.
     */
    List<ChangeRequestRecord> list();

    MergeRequestResponse response(ChangeRequestRecord mergeRequest);
}
