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

import com.phonepe.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.arnold.models.mergequeue.ReorderMergeQueueRequest;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;

import java.util.List;

public interface MergeQueueService {
    MergeQueueResponse get(String userId);

    MergeQueueResponse add(String userId, AddMergeQueueItemRequest request);

    MergeQueueResponse remove(String userId, String itemId);

    MergeQueueResponse reorder(String userId, ReorderMergeQueueRequest request);

    EnrichedMergeQueueResponse reorderPriority(String userId, ReorderMergeQueueRequest request);

    EnrichedMergeQueueResponse getEnriched(String userId);

    void reconcile(String userId, List<UserChangeRequest> associations);

    void replaceMembersWithGroup(String userId, String groupId, List<String> mergeRequestIds);

    void removeGroup(String userId, String groupId);
}
