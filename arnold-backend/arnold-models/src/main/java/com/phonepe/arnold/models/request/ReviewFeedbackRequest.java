package com.phonepe.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewFeedbackRequest {

    @Valid
    @NotNull
    ReviewMetadata metadata;

    @Valid
    @NotNull
    @NotEmpty
    List<ReviewComment> comments;
}
