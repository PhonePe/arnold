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

package com.phonepe.arnold.models.callback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabWebhookRequest(
        @JsonProperty("object_kind") String objectKind,
        Project project,
        User user,
        @JsonProperty("object_attributes") Attributes attributes,
        @JsonProperty("merge_request") MergeRequest mergeRequest,
        List<User> assignees,
        List<User> reviewers,
        List<Label> labels,
        JsonNode changes
) {
    public GitLabWebhookRequest {
        changes = changes != null && changes.isNull() ? null : changes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(long id, @JsonProperty("path_with_namespace") String pathWithNamespace) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id, String username, String name, String state) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MergeRequest(long id, long iid, @JsonProperty("target_project_id") Long targetProjectId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
            long id,
            long iid,
            String title,
            String description,
            String url,
            String action,
            String state,
            @JsonProperty("merge_status") String mergeStatus,
            @JsonProperty("detailed_merge_status") String detailedMergeStatus,
            @JsonProperty("source_branch") String sourceBranch,
            @JsonProperty("target_branch") String targetBranch,
            @JsonProperty("last_commit") Commit lastCommit,
            @JsonProperty("author_id") Long authorId,
            @JsonProperty("updated_at") String updatedAt,
            @JsonProperty("discussion_id") String discussionId,
            @JsonProperty("noteable_type") String noteableType,
            @JsonProperty("noteable_id") Long noteableId,
            @JsonProperty("note") String note,
            @JsonProperty("resolved") Boolean resolved,
            @JsonProperty("system") Boolean system,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("blocking_discussions_resolved") Boolean blockingDiscussionsResolved,
            @JsonAlias("work_in_progress") Boolean draft,
            @JsonProperty("has_conflicts") Boolean hasConflicts,
            @JsonProperty("merged_at") String mergedAt,
            @JsonProperty("closed_at") String closedAt,
            String status,
            Boolean resolvable,
            @JsonProperty("individual_note") Boolean individualNote
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String id) {
    }
}
