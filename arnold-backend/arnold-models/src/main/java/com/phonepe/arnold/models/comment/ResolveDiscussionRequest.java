package com.phonepe.arnold.models.comment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Positive;

@Value
@Builder
@Jacksonized
public class ResolveDiscussionRequest {
    @Builder.Default
    CreateDiscussionRequest.VersionControlProvider provider = CreateDiscussionRequest.VersionControlProvider.GITLAB;
    @Positive
    long projectId;
    @Positive
    long mrIid;
    boolean resolved;
}
