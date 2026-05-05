package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewMetadata {

    @NotNull
    @JsonProperty("project_id")
    private Long projectId;

    @NotNull
    @JsonProperty("merge_request_id")
    private Long mergeRequestId;

    @NotNull
    @JsonProperty("mr_iid")
    private Long mrIid;

    @NotNull
    @JsonProperty("mr_author_id")
    private Long mrAuthorId;
}
