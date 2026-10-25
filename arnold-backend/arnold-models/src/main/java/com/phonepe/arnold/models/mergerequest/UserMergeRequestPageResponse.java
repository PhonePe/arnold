package com.phonepe.arnold.models.mergerequest;

import com.phonepe.arnold.models.sync.UserSyncStatusResponse;

import java.util.List;
import java.util.Map;

public record UserMergeRequestPageResponse(
        List<UserMergeRequestResponse> items,
        String nextCursor,
        Map<String, UserSyncStatusResponse> syncs
) {
    public UserMergeRequestPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        syncs = syncs == null ? Map.of() : Map.copyOf(syncs);
    }
}
