package com.phonepe.arnold.core.model.copilot;

import com.livetheoogway.crudstore.core.Id;

public record CopilotExecutionGrant(String id, String userId, String connectionId, String workflow,
                                    String runId, String executionId, long expiresAt) implements Id { }
