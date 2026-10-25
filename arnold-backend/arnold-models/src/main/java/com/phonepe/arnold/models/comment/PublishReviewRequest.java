package com.phonepe.arnold.models.comment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Pattern;

@Value
@Builder
@Jacksonized
public class PublishReviewRequest {
    @Positive
    long projectId;
    @Positive
    long mrIid;
    String summary;
    @NotBlank
    @Pattern(regexp = "reviewed|requested_changes|approved")
    String reviewerState;
}
