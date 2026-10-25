package com.phonepe.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewComment {

    @NotNull
    @JsonProperty("note_id")
    Long noteId;

    @NotNull
    @JsonProperty("discussion_id")
    String discussionId;

    @NotNull
    @JsonProperty("comment_type")
    CommentType commentType;

    @NotNull
    @JsonProperty("has_suggestion")
    Boolean hasSuggestion;

    @NotNull
    AcceptanceStatus status;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    BigDecimal score;

    String reasoning;

    @NotNull
    Priority priority;

    @NotNull
    @JsonProperty("comment_category")
    String commentCategory;

    @JsonProperty("source_harness")
    String sourceHarness;

    @JsonProperty("source_model")
    String sourceModel;

    @JsonProperty("persona_id")
    String personaId;

    @JsonProperty("persona_version")
    Long personaVersion;
}
