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

package com.phonepe.arnold.models.analysis;

import lombok.With;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@With
public record GroupAnalysisResponse(
        String runId, String groupId, String groupName, String executionId, String runStatus,
        String invokedBy,
        List<Member> members, Map<String, String> memberStatuses, List<Finding> findings,
        List<AnalysisPersonaResponse> reviewPersonas,
        String detail, Long pipelineId, String pipelineUrl, boolean stale,
        long createdAt, long updatedAt, ExecutionOptions execution, TokenUsage usage
) {
    public GroupAnalysisResponse(String runId, String groupId, String groupName, String executionId, String runStatus,
                                 List<Member> members, Map<String, String> memberStatuses, List<Finding> findings,
                                 List<AnalysisPersonaResponse> reviewPersonas, String detail, Long pipelineId,
                                 String pipelineUrl, boolean stale, long createdAt, long updatedAt) {
        this(runId, groupId, groupName, executionId, runStatus, null, members, memberStatuses, findings, reviewPersonas,
                detail, pipelineId, pipelineUrl, stale, createdAt, updatedAt, null, null);
    }

    public GroupAnalysisResponse(String runId, String groupId, String groupName, String executionId, String runStatus,
                                 List<Member> members, Map<String, String> memberStatuses, List<Finding> findings,
                                 List<AnalysisPersonaResponse> reviewPersonas, String detail, Long pipelineId,
                                 String pipelineUrl, boolean stale, long createdAt, long updatedAt,
                                 ExecutionOptions execution) {
        this(runId, groupId, groupName, executionId, runStatus, null, members, memberStatuses, findings, reviewPersonas,
                detail, pipelineId, pipelineUrl, stale, createdAt, updatedAt, execution, null);
    }

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
                               @Size(max = 3) List<@NotBlank String> reviewPersonaIds,
                               @Valid ExecutionOptions execution) {
        public StartRequest(String rfcUrl, List<String> jiraTickets, String reviewContext, List<String> reviewPersonaIds) {
            this(rfcUrl, jiraTickets, reviewContext, reviewPersonaIds, null);
        }
    }

    @With
    public record Update(@NotBlank String executionId, @NotBlank String workerId,
                         @Positive long sequence,
                         @NotBlank @Pattern(regexp = "running|completed|failed|partial|stale") String status,
                         @NotNull Map<@NotBlank String, @Pattern(regexp = "preparing|reviewing|reviewed|failed") String> memberStatuses,
                         @Valid @NotNull @Size(max = 500) List<@NotNull Finding> findings,
                         @Size(max = 2000) String detail,
                         @Valid @Size(max = 100) Map<@NotBlank String, @NotNull MemberReview> memberReviews,
                         Boolean analysisComplete,
                         @Valid TokenUsage usage) {
        public Update(String executionId, String workerId, long sequence, String status,
                      Map<String, String> memberStatuses, List<Finding> findings, String detail,
                      Map<String, MemberReview> memberReviews, Boolean analysisComplete) {
            this(executionId, workerId, sequence, status, memberStatuses, findings, detail, memberReviews,
                    analysisComplete, null);
        }
    }

    public record MemberReview(@NotBlank String headSha,
                               @NotNull @Size(max = 500) List<@NotNull Map<String, Object>> findings) { }

    public record Persona(String id, String name, String instructions, long version) { }

    public record Manifest(GroupAnalysisResponse run, StartRequest context, List<Persona> reviewPersonas,
                           boolean publicationOnly, Map<String, MemberReview> memberReviews) { }
}
