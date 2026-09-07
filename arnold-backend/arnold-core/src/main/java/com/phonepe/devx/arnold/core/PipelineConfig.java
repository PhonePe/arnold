package com.phonepe.devx.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineConfig {

    @Builder.Default
    private String clioProjectId = "";

    @Builder.Default
    private String clioPipelineRef = "master";
}
