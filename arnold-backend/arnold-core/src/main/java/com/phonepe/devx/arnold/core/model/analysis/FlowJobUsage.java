package com.phonepe.devx.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;

/**
 * Token usage last reported by one Clio flow job. {@code id} is {@code <flow>:<execution>}; {@code runId} is the
 * Arnold run or Smart Diff the job served and {@code target} the MR id or group name.
 */
public record FlowJobUsage(String id, String flow, String runId, String target, String invokedBy, String harness,
                           String status, TokenUsage usage, long createdAt, long updatedAt) implements Id {
}
