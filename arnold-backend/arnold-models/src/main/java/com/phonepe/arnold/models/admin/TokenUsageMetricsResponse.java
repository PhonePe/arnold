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

package com.phonepe.arnold.models.admin;

import com.phonepe.arnold.models.analysis.TokenUsage;

import java.util.List;

/**
 * LLM token consumption of Clio flow jobs triggered by Arnold and created in {@code [from, to)}. Only jobs that
 * reported usage are counted. {@code cacheReadTokens} is the cached part of {@code inputTokens}; jobs that did not
 * report it count as zero.
 */
public record TokenUsageMetricsResponse(long from, long to, long jobs, long inputTokens, long outputTokens,
                                        long cacheReadTokens, List<FlowUsage> flows, List<ModelUsage> models,
                                        List<Job> recentJobs) {

    /** {@code flow} is the Clio workflow: {@code improve}, {@code group_review} or {@code smart_diff}. */
    public record FlowUsage(String flow, long jobs, long inputTokens, long outputTokens, long cacheReadTokens) {
    }

    public record ModelUsage(String model, long jobs, long inputTokens, long outputTokens,
                             long cacheReadTokens) {
    }

    /** {@code runId} is the Arnold run or Smart Diff the job served; {@code target} is the MR id or group name. */
    public record Job(String id, String flow, String runId, String target, String invokedBy, String harness,
                      String status, TokenUsage usage, long createdAt, long updatedAt) {
    }
}
