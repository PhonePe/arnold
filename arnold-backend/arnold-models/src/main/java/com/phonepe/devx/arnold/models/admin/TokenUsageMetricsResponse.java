package com.phonepe.devx.arnold.models.admin;

import com.phonepe.devx.arnold.models.analysis.TokenUsage;

import java.util.List;

/**
 * LLM token consumption of Clio flow jobs triggered by Arnold and created in {@code [from, to)}. Only jobs that
 * reported usage are counted.
 */
public record TokenUsageMetricsResponse(long from, long to, long jobs, long inputTokens, long outputTokens,
                                        List<FlowUsage> flows, List<ModelUsage> models, List<Job> recentJobs) {

    /** {@code flow} is the Clio workflow: {@code improve}, {@code group_review} or {@code smart_diff}. */
    public record FlowUsage(String flow, long jobs, long inputTokens, long outputTokens) {
    }

    public record ModelUsage(String model, long jobs, long inputTokens, long outputTokens) {
    }

    /** {@code runId} is the Arnold run or Smart Diff the job served; {@code target} is the MR id or group name. */
    public record Job(String id, String flow, String runId, String target, String invokedBy, String harness,
                      String status, TokenUsage usage, long createdAt, long updatedAt) {
    }
}
