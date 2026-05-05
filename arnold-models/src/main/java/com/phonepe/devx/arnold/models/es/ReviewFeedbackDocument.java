package com.phonepe.devx.arnold.models.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.CommentType;
import com.phonepe.devx.arnold.models.request.Priority;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewFeedbackDocument {

    // --- MR metadata (from request) ---
    private Long projectId;
    private Long mergeRequestId;
    private Long mrAuthorId;

    // --- Enriched fields from cibot ---
    private User mrAuthor;
    private String projectName;
    private String projectNamespace;
    private String teamId;
    private String podName;

    // --- Comment details (from request + future cibot enrichment) ---
    private Long noteId;
    private String discussionId;
    private String noteCreatedAt;
    private String comment;
    private Position position;
    private User reviewer;
    private CommentType commentType;
    private Boolean hasSuggestion;
    private AcceptanceStatus acceptanceStatus;
    private Double score;
    private String reasoning;
    private Priority priority;
    private String commentCategory;
    private Long ingestedAt;

    /**
     * Deterministic document ID for ES upsert: projectId:mergeRequestId:noteId
     */
    public String documentId() {
        return String.format("%d:%d:%d", projectId, mergeRequestId, noteId);
    }
}
