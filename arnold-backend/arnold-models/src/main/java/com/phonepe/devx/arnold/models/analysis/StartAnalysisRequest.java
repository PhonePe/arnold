package com.phonepe.devx.arnold.models.analysis;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
@Jacksonized
public class StartAnalysisRequest {
    @NotBlank
    String mergeRequestId;
    @NotEmpty
    List<@NotBlank String> personaIds;
    @Size(max = 2048)
    String rfcUrl;
    @Size(max = 10)
    List<@NotBlank @Size(max = 2048) String> jiraTickets;
    @Size(max = 4000)
    String reviewContext;
    @Size(max = 3)
    List<@NotBlank String> reviewPersonaIds;
}
