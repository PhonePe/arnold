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

package com.phonepe.arnold.models.mergerequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

@Value
@Builder
@Jacksonized
public class SmartDiffChange {
    @NotBlank
    @Size(max = 1024)
    String filePath;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer oldStartLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer oldEndLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer newStartLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer newEndLine;

    @JsonIgnore
    @AssertTrue
    public boolean isSelectorValid() {
        return completeRange(oldStartLine, oldEndLine) && completeRange(newStartLine, newEndLine)
                && (oldStartLine != null || newStartLine != null);
    }

    private boolean completeRange(final Integer start, final Integer end) {
        return start == null && end == null || start != null && end != null && end >= start;
    }
}
