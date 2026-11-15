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
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackfillRequest {

    /**
     * When true, the run reads + resolves + counts but performs no writes. Use to validate the
     * worklist and confirm SCM metadata availability before writing.
     */
    @Builder.Default
    boolean dryRun = true;

    /**
     * ES read page size. Kept at/under the fan-out executor queue capacity.
     */
    @Builder.Default
    @Min(1)
    @Max(500)
    int pageSize = 300;

    /**
     * Max in-flight records being re-enriched at once. Ceiling 20 (the getMergeRequestNote Hystrix
     * pool concurrency); default 10 to stay within the default pool too.
     */
    @Builder.Default
    @Min(1)
    @Max(20)
    int concurrency = 10;

    /**
     * Pause between pages in milliseconds, for rate smoothing during lean hours.
     */
    @Builder.Default
    @Min(0)
    long interPagePauseMs = 1000L;

    /**
     * Optional cap on number of pages to process in this invocation (0 = no cap, process until drained).
     */
    @Builder.Default
    @Min(0)
    private int maxPages = 0;
}