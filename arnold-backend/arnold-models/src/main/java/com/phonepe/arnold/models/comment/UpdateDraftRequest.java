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
    @Builder.Default
    CreateDiscussionRequest.VersionControlProvider provider = CreateDiscussionRequest.VersionControlProvider.GITLAB;
    @Positive
    long projectId;
    @Positive
    long mrIid;
    @NotBlank
    String body;
}
