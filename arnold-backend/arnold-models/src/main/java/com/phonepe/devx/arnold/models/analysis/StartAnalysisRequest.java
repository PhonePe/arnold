package com.phonepe.devx.arnold.models.analysis;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Value
@Builder
@Jacksonized
public class StartAnalysisRequest {
    @NotBlank
    String mergeRequestId;
    @NotEmpty
    List<@NotBlank String> personaIds;
}
