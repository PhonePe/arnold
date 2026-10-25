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

package com.phonepe.arnold.models.mergerequest;

import java.util.List;

public record MergeRequestResponse(
        String id,
        String provider,
        String repositoryId,
        String providerId,
        long number,
        String repositoryPath,
        String title,
        String description,
        String webUrl,
        String sourceBranch,
        String targetBranch,
        String headSha,
        String state,
        String mergeStatus,
        boolean draft,
        Long authorId,
        String authorName,
        List<Long> assigneeIds,
        List<String> assigneeNames,
        List<Long> reviewerIds,
        List<String> reviewerNames,
        List<String> labels,
        long createdAt,
        long mergedAt,
        long closedAt,
        boolean hasConflicts,
        boolean blockingDiscussionsResolved,
        long providerUpdatedAt,
        long lastSyncedAt
) {
    public MergeRequestResponse(final String id,
                                final String provider,
                                final String repositoryId,
                                final String providerId,
                                final long number,
                                final String repositoryPath,
                                final String title,
                                final String description,
                                final String webUrl,
                                final String sourceBranch,
                                final String targetBranch,
                                final String headSha,
                                final String state,
                                final String mergeStatus,
                                final boolean draft,
                                final Long authorId,
                                final List<Long> assigneeIds,
                                final List<Long> reviewerIds,
                                final List<String> labels,
                                final long providerUpdatedAt,
                                final long lastSyncedAt) {
        this(id, provider, repositoryId, providerId, number, repositoryPath, title, description, webUrl, sourceBranch,
                targetBranch, headSha, state, mergeStatus, draft, authorId, null, assigneeIds, List.of(), reviewerIds,
                List.of(), labels, 0, 0, 0, false, true, providerUpdatedAt, lastSyncedAt);
    }
}
