package com.phonepe.devx.arnold.models.callback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        List<Label> labels
) {
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
    public record MergeRequest(long id, long iid) {
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
            @JsonProperty("blocking_discussions_resolved") Boolean blockingDiscussionsResolved
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(String id) {
    }
}
