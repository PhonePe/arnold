package com.phonepe.arnold.models.mergerequest;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
@Jacksonized
public class SmartDiffGroup {
    @NotBlank
    @Size(max = 128)
    String id;
    @NotBlank
    @Size(max = 500)
    String title;
    @NotBlank
    @Size(max = 5000)
    String rationale;
    @Valid
    @NotNull
    @Size(min = 1, max = 500)
    List<@NotNull SmartDiffStep> steps;
}
