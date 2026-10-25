package com.phonepe.arnold.models.runner;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Value
@Builder
@Jacksonized
public class ClaimRunnerJobRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}")
    String workerId;
    /** Claim this specific job; otherwise the oldest runnable job is claimed. */
    @Positive
    Long job;
    /** Long-poll for up to this many seconds when no job is available. */
    @Min(0)
    @Max(30)
    int waitSeconds;
}
