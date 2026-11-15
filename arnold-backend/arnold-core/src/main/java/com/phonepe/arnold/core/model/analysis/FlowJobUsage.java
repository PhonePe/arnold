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

package com.phonepe.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.models.analysis.TokenUsage;

/**
 * Token usage last reported by one Clio flow job. {@code id} is {@code <flow>:<execution>}; {@code runId} is the
 * Arnold run or Smart Diff the job served and {@code target} the MR id or group name.
 */
public record FlowJobUsage(String id, String flow, String runId, String target, String invokedBy, String harness,
                           String status, TokenUsage usage, long createdAt, long updatedAt) implements Id {
}
