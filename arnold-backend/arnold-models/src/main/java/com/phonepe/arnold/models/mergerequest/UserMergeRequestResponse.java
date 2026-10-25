package com.phonepe.arnold.models.mergerequest;

import java.util.Set;

public record UserMergeRequestResponse(
        MergeRequestResponse mergeRequest,
        Set<String> roles,
        String reviewState,
        boolean blocked
) {
    public UserMergeRequestResponse {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
