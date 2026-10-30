package com.phonepe.devx.arnold.models.analysis;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public record ExecutionOptions(
        @NotNull @Pattern(regexp = "copilot|opencode") String harness,
        @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*") String model) {
    @javax.validation.constraints.AssertTrue(message = "OpenCode models must use provider/model format")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isModelCompatible() {
        return model == null || !"opencode".equals(harness) || model.contains("/");
    }
}
