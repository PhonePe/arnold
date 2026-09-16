package com.phonepe.devx.arnold.core.model.vcs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;
import lombok.With;

import java.util.List;
import java.util.Locale;

public record ChangeRequestRecord(
        String id,
        VersionControlProviderType provider,
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
        @With List<String> reviewerStates,
        List<String> labels,
        long createdAt,
        long mergedAt,
        long closedAt,
        boolean hasConflicts,
        boolean blockingDiscussionsResolved,
        long providerUpdatedAt,
        long lastSyncedAt,
        @With boolean projectArchived
) implements Id {

    @JsonCreator
    public ChangeRequestRecord(@JsonProperty("id") final String id,
                               @JsonProperty("provider") final VersionControlProviderType provider,
                               @JsonProperty("repositoryId") final String repositoryId,
                               @JsonProperty("providerId") final String providerId,
                               @JsonProperty("number") final long number,
                               @JsonProperty("repositoryPath") final String repositoryPath,
                               @JsonProperty("title") final String title,
                               @JsonProperty("description") final String description,
                               @JsonProperty("webUrl") final String webUrl,
                               @JsonProperty("sourceBranch") final String sourceBranch,
                               @JsonProperty("targetBranch") final String targetBranch,
                               @JsonProperty("headSha") final String headSha,
                               @JsonProperty("state") final String state,
                               @JsonProperty("mergeStatus") final String mergeStatus,
                               @JsonProperty("draft") final boolean draft,
                               @JsonProperty("authorId") final Long authorId,
                               @JsonProperty("authorName") final String authorName,
                               @JsonProperty("assigneeIds") final List<Long> assigneeIds,
                               @JsonProperty("assigneeNames") final List<String> assigneeNames,
                               @JsonProperty("reviewerIds") final List<Long> reviewerIds,
                               @JsonProperty("reviewerNames") final List<String> reviewerNames,
                               @JsonProperty("reviewerStates") final List<String> reviewerStates,
                               @JsonProperty("labels") final List<String> labels,
                               @JsonProperty("createdAt") final long createdAt,
                               @JsonProperty("mergedAt") final long mergedAt,
                               @JsonProperty("closedAt") final long closedAt,
                               @JsonProperty("hasConflicts") final boolean hasConflicts,
                               @JsonProperty("blockingDiscussionsResolved") final boolean blockingDiscussionsResolved,
                               @JsonProperty("providerUpdatedAt") final long providerUpdatedAt,
                               @JsonProperty("lastSyncedAt") final long lastSyncedAt,
                               @JsonProperty("projectArchived") final boolean projectArchived) {
        this.id = id;
        this.provider = provider;
        this.repositoryId = repositoryId;
        this.providerId = providerId;
        this.number = number;
        this.repositoryPath = repositoryPath;
        this.title = title;
        this.description = description;
        this.webUrl = webUrl;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.headSha = headSha;
        this.state = state;
        this.mergeStatus = mergeStatus;
        this.draft = draft;
        this.authorId = authorId;
        this.authorName = authorName;
        this.assigneeIds = assigneeIds == null ? List.of() : List.copyOf(assigneeIds);
        this.assigneeNames = assigneeNames == null ? List.of() : List.copyOf(assigneeNames);
        this.reviewerIds = reviewerIds == null ? List.of() : List.copyOf(reviewerIds);
        this.reviewerNames = reviewerNames == null ? List.of() : List.copyOf(reviewerNames);
        this.reviewerStates = reviewerStates == null ? List.of() : List.copyOf(reviewerStates);
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.createdAt = createdAt;
        this.mergedAt = mergedAt;
        this.closedAt = closedAt;
        this.hasConflicts = hasConflicts;
        this.blockingDiscussionsResolved = blockingDiscussionsResolved;
        this.providerUpdatedAt = providerUpdatedAt;
        this.lastSyncedAt = lastSyncedAt;
        this.projectArchived = projectArchived;
    }

    public ChangeRequestRecord(final String id,
                               final VersionControlProviderType provider,
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
        this(id, provider, repositoryId, providerId, number, repositoryPath, title, description, webUrl,
                sourceBranch, targetBranch, headSha, state, mergeStatus, draft, authorId, null, assigneeIds,
                List.of(), reviewerIds, List.of(), List.of(), labels, 0, 0, 0, false, true,
                providerUpdatedAt, lastSyncedAt, false);
    }

    @JsonIgnore
    public boolean isReadyToMerge() {
        return !projectArchived && !draft && isOpen() && !isBlocked()
                && "mergeable".equalsIgnoreCase(mergeStatus);
    }

    @JsonIgnore
    public boolean isOpen() {
        return "opened".equalsIgnoreCase(state) || "open".equalsIgnoreCase(state);
    }

    @JsonIgnore
    public boolean isBlocked() {
        final var status = mergeStatus == null ? "" : mergeStatus.toLowerCase(Locale.ROOT);
        return hasConflicts || !blockingDiscussionsResolved || switch (status) {
            case "cannot_be_merged", "conflict", "discussions_not_resolved", "not_approved", "ci_must_pass",
                    "ci_still_running", "commits_status", "draft_status", "external_status_checks",
                    "jira_association_missing", "merge_request_blocked", "merge_time", "need_rebase",
                    "not_open", "policies_denied", "requested_changes", "security_policy_violations",
                    "status_checks_must_pass", "security_policy_pipeline_check", "locked_paths", "locked_lfs_files",
                    "title_regex" -> true;
            default -> false;
        };
    }

    public static String key(final VersionControlProviderType provider, final String repositoryId, final long number) {
        return provider.name().toLowerCase() + ":" + repositoryId + ":" + number;
    }
}
