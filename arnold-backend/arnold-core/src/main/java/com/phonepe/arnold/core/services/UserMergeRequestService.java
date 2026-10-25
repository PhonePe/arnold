/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;

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
