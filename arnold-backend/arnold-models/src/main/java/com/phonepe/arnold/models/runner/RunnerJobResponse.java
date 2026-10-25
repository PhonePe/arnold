package com.phonepe.arnold.models.runner;

import java.util.Map;

/**
 * A runner job leased to a worker. {@code variables} are exported to the workflow as environment variables.
 */
public record RunnerJobResponse(long number, String workflow, Map<String, String> variables, String status,
                                int attempt, long leaseExpiresAt) {
}
