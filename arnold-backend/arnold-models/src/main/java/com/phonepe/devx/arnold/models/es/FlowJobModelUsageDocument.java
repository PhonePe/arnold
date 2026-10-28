package com.phonepe.devx.arnold.models.es;

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
    public static final String TYPE = "flow_job_model_usage";
}
