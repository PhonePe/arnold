package com.phonepe.devx.arnold.models.es;

import java.util.List;

/** Token usage of one Clio flow job; {@code models} only lists names, see {@link FlowJobModelUsageDocument}. */
public record FlowJobUsageDocument(
        String id,
        String type,
        String flow,
        String runId,
        String target,
        String invokedBy,
        String harness,
        String status,
        String jobUrl,
        List<String> models,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        Long cacheReadTokens,
        long createdAt,
        long updatedAt
) {
    public static final String TYPE = "flow_job_usage";
}
