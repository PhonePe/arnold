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
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewFeedbackDocument {

    // --- MR metadata (from request) ---
    Long projectId;
    Long mergeRequestId;
    Long mrIid;
    Long mrAuthorId;

    // --- Enriched fields from cibot ---
    User mrAuthor;
    String projectName;
    String projectNamespace;
    String teamId;
    String podName;

    // --- Comment details (from request + future cibot enrichment) ---
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
    Long ingestedAt;

    /**
     * Deterministic document ID for ES upsert: projectId:mergeRequestId:noteId
     */
    public String documentId() {
        return String.format("%d:%d:%d", projectId, mergeRequestId, noteId);
    }
}
