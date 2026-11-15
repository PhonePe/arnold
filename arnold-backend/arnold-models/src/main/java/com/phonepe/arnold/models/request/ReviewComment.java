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
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewComment {

    @NotNull
    @JsonProperty("note_id")
    Long noteId;

    @NotNull
    @JsonProperty("discussion_id")
    String discussionId;

    @NotNull
    @JsonProperty("comment_type")
    CommentType commentType;

    @NotNull
    @JsonProperty("has_suggestion")
    Boolean hasSuggestion;

    @NotNull
    AcceptanceStatus status;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    BigDecimal score;

    String reasoning;

    @NotNull
    Priority priority;

    @NotNull
    @JsonProperty("comment_category")
    String commentCategory;

    @JsonProperty("source_harness")
    String sourceHarness;

    @JsonProperty("source_model")
    String sourceModel;

    @JsonProperty("persona_id")
    String personaId;

    @JsonProperty("persona_version")
    Long personaVersion;
}
