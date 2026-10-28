package com.phonepe.devx.arnold.core.model.events;

import com.phonepe.devx.arnold.core.events.ArnoldSignal;
import com.phonepe.devx.arnold.core.model.analysis.FlowJobUsage;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;

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
