/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.models.request;

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
