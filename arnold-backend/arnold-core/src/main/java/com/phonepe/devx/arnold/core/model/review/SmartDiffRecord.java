package com.phonepe.devx.arnold.core.model.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffGroup;

import java.util.List;

public record SmartDiffRecord(String id, String headSha, String executionId, String status, String summary,
                              List<SmartDiffGroup> groups, long updatedAt, String errorMessage,
                              String commentAssessmentStatus, int assessedCommentCount, String requestedBy,
                              ExecutionOptions execution)
        implements Id {
    @JsonCreator
    public SmartDiffRecord(@JsonProperty("id") final String id,
                           @JsonProperty("headSha") final String headSha,
                           @JsonProperty("executionId") final String executionId,
                           @JsonProperty("status") final String status,
                           @JsonProperty("summary") final String summary,
                           @JsonProperty("groups") final List<SmartDiffGroup> groups,
                           @JsonProperty("updatedAt") final long updatedAt,
                           @JsonProperty("errorMessage") final String errorMessage,
                           @JsonProperty("commentAssessmentStatus") final String commentAssessmentStatus,
                           @JsonProperty("assessedCommentCount") final int assessedCommentCount,
                           @JsonProperty("requestedBy") final String requestedBy,
                           @JsonProperty("execution") final ExecutionOptions execution) {
        this.id = id;
        this.headSha = headSha;
        this.executionId = executionId;
        this.status = status;
        this.summary = summary;
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.updatedAt = updatedAt;
        this.errorMessage = errorMessage;
        this.commentAssessmentStatus = commentAssessmentStatus == null ? "not_requested" : commentAssessmentStatus;
        this.assessedCommentCount = assessedCommentCount;
        this.requestedBy = requestedBy;
        this.execution = execution;
    }

    public SmartDiffRecord(final String id, final String headSha, final String status,
                           final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage) {
        this(id, headSha, null, status, null, groups, updatedAt, errorMessage, "not_requested", 0, null, null);
    }

    public SmartDiffRecord(final String id, final String headSha, final String executionId, final String status,
                           final String summary, final List<SmartDiffGroup> groups, final long updatedAt,
                           final String errorMessage) {
        this(id, headSha, executionId, status, summary, groups, updatedAt, errorMessage, "not_requested", 0, null, null);
    }

    public static String key(final long projectId, final long iid, final String headSha) {
        return "smart-diff:gitlab:" + projectId + ":" + iid + ":" + headSha;
    }
}
