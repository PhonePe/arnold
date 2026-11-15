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

package com.phonepe.arnold.models.analysis;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.Valid;
import java.util.List;

@Value
@Builder
@Jacksonized
public class PersonaCompletionRequest {
    @NotBlank
    String mergeRequestId;
    @NotBlank
    String runId;
    @NotBlank
    String personaId;
    @NotNull
    Outcome outcome;
    String detail;
    @Valid
    @NotNull
    @Size(max = 500)
    @Builder.Default
    List<@NotNull AnalysisFindingResponse> findings = List.of();
    @Valid
    TokenUsage usage;

    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
