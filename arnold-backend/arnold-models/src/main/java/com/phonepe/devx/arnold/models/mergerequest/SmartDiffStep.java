package com.phonepe.devx.arnold.models.mergerequest;

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
public class SmartDiffStep {
    @NotBlank
    @Size(max = 128)
    String id;
    @NotBlank
    @Size(max = 500)
    String title;
    @NotBlank
    @Size(max = 5000)
    String explanation;
    @Valid
    @NotNull
    @Size(min = 1, max = 5000)
    List<@NotNull SmartDiffChange> changes;
}
