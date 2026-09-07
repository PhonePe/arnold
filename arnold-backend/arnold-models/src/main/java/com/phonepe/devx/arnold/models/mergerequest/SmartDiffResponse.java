package com.phonepe.devx.arnold.models.mergerequest;

import java.util.List;

public record SmartDiffResponse(String headSha, String status, String summary, List<SmartDiffGroup> groups,
                                long updatedAt, String errorMessage) {
    public SmartDiffResponse(final String headSha, final String status, final List<SmartDiffGroup> groups,
                             final long updatedAt, final String errorMessage) {
        this(headSha, status, null, groups, updatedAt, errorMessage);
    }
}
