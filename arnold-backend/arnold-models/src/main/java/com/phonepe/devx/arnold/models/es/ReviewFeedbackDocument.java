package com.phonepe.devx.arnold.models.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.CommentType;
import com.phonepe.devx.arnold.models.request.Priority;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewFeedbackDocument {

    Long mrId;
    Long mrIid;
    Long mrAuthorId;
    String assessedSha;
    User mrAuthor;
    ProjectInfo project;
    Long noteId;
    String discussionId;
    String noteCreatedAt;
    String comment;
    Position position;
    User reviewer;
    CommentType commentType;
    Boolean hasSuggestion;
    AcceptanceStatus acceptanceStatus;
    Double score;
    String reasoning;
    Priority priority;
    String commentCategory;
    String sourceHarness;
    String sourceModel;
    String analysisRunId;
    String personaId;
    Long personaVersion;
    Long ingestedAt;

    /**
     * Deterministic document ID for ES upsert: projectId:mrId:noteId
     */
    public String documentId() {
        final Long projectId = project != null ? project.getProjectId() : null;
        return projectId + ":" + mrId + ":" + noteId;
    }
}
