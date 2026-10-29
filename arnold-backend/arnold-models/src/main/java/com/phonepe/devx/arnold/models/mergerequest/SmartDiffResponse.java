package com.phonepe.devx.arnold.models.mergerequest;

import com.phonepe.devx.arnold.models.analysis.TokenUsage;

import java.util.List;

public record SmartDiffResponse(String headSha, String status, String summary, List<SmartDiffGroup> groups,
                                long updatedAt, String errorMessage, String commentAssessmentStatus,
                                int assessedCommentCount, TokenUsage usage, Long pipelineId,
                                String pipelineUrl) {
    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage,
                             final String commentAssessmentStatus, final int assessedCommentCount,
                             final TokenUsage usage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, commentAssessmentStatus, assessedCommentCount,
                usage, null, null);
    }

    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage,
                             final String commentAssessmentStatus, final int assessedCommentCount) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, commentAssessmentStatus, assessedCommentCount,
                null);
    }

    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, "not_requested", 0, null);
    }

    public SmartDiffResponse(final String headSha, final String status, final List<SmartDiffGroup> groups,
                             final long updatedAt, final String errorMessage) {
        this(headSha, status, null, groups, updatedAt, errorMessage, "not_requested", 0, null);
    }
}
