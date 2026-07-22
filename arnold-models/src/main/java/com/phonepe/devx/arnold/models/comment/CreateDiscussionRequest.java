package com.phonepe.devx.arnold.models.comment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Value
@Builder
@Jacksonized
public class CreateDiscussionRequest {
    @NotNull
    VersionControlProvider provider;
    @Positive
    long projectId;
    @Positive
    long mrIid;
    @NotBlank
    String body;
    @NotBlank
    String clientOperationId;

    public enum VersionControlProvider {
        GITLAB,
        GITHUB
    }
}
