package com.phonepe.devx.arnold.models.admin;

import java.util.List;

/**
 * LLM token consumption of analysis runs created in {@code [from, to)}. {@code reportedRuns} counts runs whose
 * executing job reported usage; older runs and runs whose job never called back carry none.
 */
public record TokenUsageMetricsResponse(long from, long to, long runs, long reportedRuns, long inputTokens,
                                        long outputTokens, List<ModelUsage> models, List<Run> recentRuns) {

    public record ModelUsage(String model, long runs, long inputTokens, long outputTokens) {
    }

    /** {@code kind} is {@code analysis} or {@code group_analysis}; {@code target} is the MR id or group name. */
    public record Run(String runId, String kind, String target, String invokedBy, String harness, String status,
                      long inputTokens, long outputTokens, long createdAt) {
    }
}
