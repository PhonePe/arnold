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

package com.phonepe.arnold.models.analysis;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public record AnalysisFindingResponse(
        @NotBlank @Size(max = 128) String id,
        @NotBlank String headSha,
        @Size(max = 1024) String filePath,
        @Size(max = 1024) String oldPath,
        @Positive Integer newLine,
        @NotBlank @Size(max = 20000) String body,
        @NotBlank @Pattern(regexp = "posted|failed|unknown") String publicationStatus,
        @Size(max = 2048) String commentUrl,
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}") String personaId,
        @PositiveOrZero Long personaVersion
) {
    /**
     * A finding from the general Improve pass, not attributed to any review persona.
     */
    public AnalysisFindingResponse(final String id, final String headSha, final String filePath, final String oldPath,
                                   final Integer newLine, final String body, final String publicationStatus,
                                   final String commentUrl) {
        this(id, headSha, filePath, oldPath, newLine, body, publicationStatus, commentUrl, null, null);
    }
}
