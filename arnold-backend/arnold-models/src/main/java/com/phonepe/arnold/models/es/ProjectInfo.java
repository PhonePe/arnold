package com.phonepe.arnold.models.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectInfo {

    Long projectId;
    String projectName;
    String projectNamespace;
    String teamId;
    String podName;
}
