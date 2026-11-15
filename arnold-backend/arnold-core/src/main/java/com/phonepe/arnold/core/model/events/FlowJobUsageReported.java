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

package com.phonepe.arnold.core.model.events;

import com.phonepe.arnold.core.events.ArnoldSignal;
import com.phonepe.arnold.core.model.analysis.FlowJobUsage;
import com.phonepe.arnold.models.analysis.ExecutionOptions;
import com.phonepe.arnold.models.analysis.TokenUsage;

public record FlowJobUsageReported(FlowJobUsage job) implements ArnoldSignal {

    public static FlowJobUsageReported of(final String flow, final String execution, final String runId,
                                          final String target, final String invokedBy,
                                          final ExecutionOptions options, final String status,
                                          final TokenUsage usage, final long createdAt) {
        return new FlowJobUsageReported(new FlowJobUsage(flow + ":" + execution, flow, runId, target, invokedBy,
                options == null ? null : options.harness(), status, usage, createdAt, System.currentTimeMillis()));
    }

    @Override
    public String id() {
        return job.id();
    }

    @Override
    public long occurredAt() {
        return job.updatedAt();
    }
}
