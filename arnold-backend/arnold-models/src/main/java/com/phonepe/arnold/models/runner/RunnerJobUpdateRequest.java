package com.phonepe.arnold.models.runner;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Value
@Builder
@Jacksonized
public class RunnerJobUpdateRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}")
    String workerId;
    /** For completion only: whether the workflow succeeded. */
    boolean succeeded;
    @Size(max = 2000)
    String detail;
}
