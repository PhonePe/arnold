/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.models.runner;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;

@Value
@Builder
@Jacksonized
public class ClaimRunnerJobRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,128}")
    String workerId;
    /** Claim this specific job; otherwise the oldest runnable job is claimed. */
    @Positive
    Long job;
    /** Long-poll for up to this many seconds when no job is available. */
    @Min(0)
    @Max(30)
    int waitSeconds;
}
