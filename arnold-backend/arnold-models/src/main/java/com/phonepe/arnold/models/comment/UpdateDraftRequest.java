package com.phonepe.arnold.models.comment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

@Value
@Builder
@Jacksonized
public class UpdateDraftRequest {
    @Positive
    long projectId;
    @Positive
    long mrIid;
    @NotBlank
    String body;
}
