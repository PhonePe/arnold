package com.phonepe.arnold.models.mergequeue;

import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;

import java.util.List;

public record MergeRequestGroupResponse(
        String id,
        String name,
        List<UserMergeRequestResponse> members,
        long createdAt,
        long updatedAt
) {
    public MergeRequestGroupResponse {
        members = members == null ? List.of() : List.copyOf(members);
    }
}
