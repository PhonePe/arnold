package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewMetadata {

    @NotNull
    @JsonProperty("project_id")
    private Long projectId;

    @NotNull
    @JsonProperty("mr_id")
    private Long mrId;

    @NotNull
    @JsonProperty("mr_iid")
    private Long mrIid;

    @NotNull
    @JsonProperty("mr_author_id")
    private Long mrAuthorId;

    @JsonProperty("assessed_sha")
    @Pattern(regexp = "[0-9a-fA-F]{40,64}")
    private String assessedSha;

    @JsonProperty("analysis_run_id")
    private String analysisRunId;
}
