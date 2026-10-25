/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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
