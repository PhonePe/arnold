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

    MergeRequestResponse addReviewer(long projectId, long iid, String username, String userId);

    MergeRequestResponse removeReviewer(long projectId, long iid, long reviewerId, String userId);

    List<ReviewerCandidateResponse> searchReviewers(long projectId, String query, String userId);

    MergeRequestReviewResponse getReview(long projectId, long iid, String userId);

    String getDiff(long projectId, long iid, String userId);

    String getDiffVersion(long projectId, long iid, long versionId, String userId);

    List<MergeRequestDiffVersionResponse> listDiffVersions(long projectId, long iid, String userId);

    MergeRequestFilesResponse getFiles(long projectId, long iid, String oldPath, String newPath, String baseSha,
                                       String headSha, String userId);

    ViewedFilesResponse getViewedFiles(long projectId, long iid, String headSha, String userId);

    ViewedFilesResponse updateViewedFiles(long projectId, long iid, ViewedFilesRequest request, String userId);

    SmartDiffResponse getSmartDiff(long projectId, long iid, String headSha);

    SmartDiffResponse requestSmartDiff(long projectId, long iid, SmartDiffRequest request, String userId);

    SmartDiffResponse updateSmartDiff(long projectId, long iid, SmartDiffResultRequest request);

    boolean synchronize(ChangeRequestRecord mergeRequest);

    Optional<ChangeRequestRecord> record(String mergeRequestId);

    Map<String, ChangeRequestRecord> records(Collection<String> mergeRequestIds);

    MergeRequestResponse response(ChangeRequestRecord mergeRequest);
}
