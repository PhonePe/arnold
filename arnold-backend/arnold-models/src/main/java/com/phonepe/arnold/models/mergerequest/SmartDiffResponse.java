package com.phonepe.arnold.models.mergerequest;

import java.util.List;

public record SmartDiffResponse(String headSha, String status, String summary, List<SmartDiffGroup> groups,
                                long updatedAt, String errorMessage, String commentAssessmentStatus,
                                int assessedCommentCount) {
    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, "not_requested", 0);
    }

    public SmartDiffResponse(final String headSha, final String status, final List<SmartDiffGroup> groups,
                             final long updatedAt, final String errorMessage) {
        this(headSha, status, null, groups, updatedAt, errorMessage, "not_requested", 0);
    }
}
