package com.phonepe.arnold.models.mergerequest;

import java.util.List;

public record MergeRequestReviewResponse(
        String detailedMergeStatus,
        boolean hasConflicts,
        boolean discussionsResolved,
        String pipelineStatus,
        String pipelineUrl,
        int approvalsRequired,
        int approvalsLeft,
        boolean approved,
        List<ReviewerStatusResponse> reviewers
) {
}
