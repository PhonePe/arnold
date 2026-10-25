package com.phonepe.arnold.core.model.runner;

import com.livetheoogway.crudstore.core.Id;

import java.util.Map;

/**
 * A job queued for self-hosted runner workers ({@code runner.mode: LOCAL}).
 */
public record RunnerJob(String id,
                        long number,
                        String workflow,
                        Map<String, String> variables,
                        Status status,
                        String workerId,
                        int attempts,
                        long leaseExpiresAt,
                        String detail,
                        long createdAt,
                        long updatedAt) implements Id {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public static String key(final long number) {
        return "runner-job:" + number;
    }

    public boolean terminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }

    public RunnerJob with(final Status newStatus, final String newWorkerId, final int newAttempts,
                          final long newLeaseExpiresAt, final String newDetail, final long now) {
        return new RunnerJob(id, number, workflow, variables, newStatus, newWorkerId, newAttempts, newLeaseExpiresAt,
                newDetail, createdAt, now);
    }
}
