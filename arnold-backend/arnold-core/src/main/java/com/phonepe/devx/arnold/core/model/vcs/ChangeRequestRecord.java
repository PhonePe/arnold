package com.phonepe.devx.arnold.core.model.vcs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;

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
        List<Long> assigneeIds,
        List<Long> reviewerIds,
        List<String> labels,
        long providerUpdatedAt,
        long lastSyncedAt
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
                               @JsonProperty("assigneeIds") final List<Long> assigneeIds,
                               @JsonProperty("reviewerIds") final List<Long> reviewerIds,
                               @JsonProperty("labels") final List<String> labels,
                               @JsonProperty("providerUpdatedAt") final long providerUpdatedAt,
                               @JsonProperty("lastSyncedAt") final long lastSyncedAt) {
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
        this.assigneeIds = assigneeIds == null ? List.of() : List.copyOf(assigneeIds);
        this.reviewerIds = reviewerIds == null ? List.of() : List.copyOf(reviewerIds);
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.providerUpdatedAt = providerUpdatedAt;
        this.lastSyncedAt = lastSyncedAt;
    }

    public static String key(final VersionControlProviderType provider, final String repositoryId, final long number) {
        return provider.name().toLowerCase() + ":" + repositoryId + ":" + number;
    }
}
