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

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public record ExecutionOptions(
        @NotNull @Pattern(regexp = "copilot|opencode") String harness,
        @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*") String model) {
    public ExecutionOptions {
        // A blank model means "deployment default".
        model = model == null || model.isBlank() ? null : model.trim();
    }

    @javax.validation.constraints.AssertTrue(message = "OpenCode models must use provider/model format")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isModelCompatible() {
        return model == null || !"opencode".equals(harness) || model.contains("/");
    }
}
