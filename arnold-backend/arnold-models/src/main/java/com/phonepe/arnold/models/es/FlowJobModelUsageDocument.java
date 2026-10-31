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

package com.phonepe.arnold.models.es;

/** Tokens one model used in a Clio flow job, kept apart so per-model sums aggregate correctly. */
public record FlowJobModelUsageDocument(
        String id,
        String type,
        String jobId,
        String flow,
        String runId,
        String invokedBy,
        String harness,
        String model,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        Long cacheReadTokens,
        long createdAt,
        long updatedAt
) {
    public static final String DOCUMENT_TYPE = "flow_job_model_usage";
}
