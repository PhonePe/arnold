package com.phonepe.devx.arnold.models.mergerequest;

import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;

import java.util.List;

public record UserMergeRequestPageResponse(
        List<UserMergeRequestResponse> items,
        String nextCursor,
        UserSyncStatusResponse sync
) {
    public UserMergeRequestPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
