package com.phonepe.arnold.core.model.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.analysis.ExecutionOptions;
import com.phonepe.arnold.models.mergerequest.SmartDiffGroup;

import java.util.List;

public record SmartDiffRecord(String id, String headSha, String executionId, String status, String summary,
                              List<SmartDiffGroup> groups, long updatedAt, String errorMessage,
                              String commentAssessmentStatus, int assessedCommentCount, String requestedBy,
                              ExecutionOptions execution, long requestedAt)
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
                           @JsonProperty("execution") final ExecutionOptions execution,
                           @JsonProperty("requestedAt") final long requestedAt) {
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
        this.requestedAt = requestedAt;
    }

    public SmartDiffRecord(final String id, final String headSha, final String executionId, final String status,
                           final String summary, final List<SmartDiffGroup> groups, final long updatedAt,
                           final String errorMessage, final String commentAssessmentStatus,
                           final int assessedCommentCount, final String requestedBy,
                           final ExecutionOptions execution) {
        this(id, headSha, executionId, status, summary, groups, updatedAt, errorMessage, commentAssessmentStatus,
                assessedCommentCount, requestedBy, execution, 0);
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

    public static String key(final VersionControlProviderType provider, final long projectId, final long iid,
                             final String headSha) {
        return "smart-diff:" + provider.name().toLowerCase(java.util.Locale.ROOT) + ":" + projectId + ":" + iid
                + ":" + headSha;
    }
}
