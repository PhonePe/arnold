package com.phonepe.arnold.models.mergequeue;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Value
@Builder
@Jacksonized
public class AddMergeQueueItemRequest {
    @NotBlank
    String mergeRequestId;
    @NotBlank
    @Pattern(regexp = "author|reviewer|watcher")
    String role;
}
