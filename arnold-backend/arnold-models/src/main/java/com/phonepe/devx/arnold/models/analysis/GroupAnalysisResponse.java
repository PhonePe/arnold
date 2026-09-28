package com.phonepe.devx.arnold.models.analysis;

import lombok.With;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@With
public record GroupAnalysisResponse(
        String runId, String groupId, String groupName, String executionId, String runStatus,
        List<Member> members, Map<String, String> memberStatuses, List<Finding> findings,
        List<AnalysisPersonaResponse> reviewPersonas,
        String detail, Long pipelineId, String pipelineUrl, boolean stale,
        long createdAt, long updatedAt
) {
    public GroupAnalysisResponse {
        reviewPersonas = reviewPersonas == null ? List.of() : List.copyOf(reviewPersonas);
    }

    public record Member(String mergeRequestId, long projectId, long iid, String repositoryPath,
                         String title, String description, String webUrl, String sourceBranch, String targetBranch,
                         String baseSha, String startSha, String headSha) { }

    public record Finding(
            @NotBlank @Size(max = 128) String id, @NotBlank String mergeRequestId,
            @NotBlank String headSha, @Size(max = 1024) String filePath, @Size(max = 1024) String oldPath,
            @Positive Integer newLine, @NotBlank @Size(max = 20000) String body,
            @NotNull @Size(max = 100) List<@NotBlank String> relatedMergeRequestIds,
            @NotBlank @Pattern(regexp = "pending|posted|failed|unknown") String publicationStatus,
            @Size(max = 2048) String commentUrl
    ) { }

    public record StartRequest(@Size(max = 2048) String rfcUrl,
                               @Size(max = 10) List<@NotBlank @Size(max = 2048) String> jiraTickets,
                               @Size(max = 4000) String reviewContext,
                               @Size(max = 3) List<@NotBlank String> reviewPersonaIds) { }

    @With
    public record Update(@NotBlank String executionId, @NotBlank String workerId,
                         @Positive long sequence,
                         @NotBlank @Pattern(regexp = "running|completed|failed|partial|stale") String status,
                         @NotNull Map<@NotBlank String, @Pattern(regexp = "preparing|reviewing|reviewed|failed") String> memberStatuses,
                         @Valid @NotNull @Size(max = 500) List<@NotNull Finding> findings,
                         @Size(max = 2000) String detail,
                         @Valid @Size(max = 100) Map<@NotBlank String, @NotNull MemberReview> memberReviews,
                         Boolean analysisComplete) { }

    public record MemberReview(@NotBlank String headSha,
                               @NotNull @Size(max = 500) List<@NotNull Map<String, Object>> findings) { }

    public record Persona(String id, String name, String instructions, long version) { }

    public record Manifest(GroupAnalysisResponse run, StartRequest context, List<Persona> reviewPersonas,
                           boolean publicationOnly, Map<String, MemberReview> memberReviews) { }
}
