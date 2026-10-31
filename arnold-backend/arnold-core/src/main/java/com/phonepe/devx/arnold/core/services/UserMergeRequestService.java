package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserMergeRequestService {
    List<UserChangeRequest> associationsForUser(String userId);

    List<UserChangeRequest> associationsForMergeRequest(String mergeRequestId);

    UserChangeRequest upsert(String userId, ChangeRequestRecord mergeRequest, Set<MergeRequestRole> roles, long seenAt);

    UserChangeRequest deactivate(UserChangeRequest association, ChangeRequestRecord mergeRequest, long seenAt);

    default UserMergeRequestPageResponse list(String userId, String role, int limit, String cursor) {
        return list(userId, role, limit, cursor, "open");
    }

    UserMergeRequestPageResponse list(String userId, String role, int limit, String cursor, String state);

    Map<String, UserMergeRequestResponse> summaries(String userId, Collection<String> mergeRequestIds);
}
