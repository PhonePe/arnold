/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.config;

public record UserSyncConfiguration(
        long intervalMs,
        long leaseMs,
        int workerBatchSize,
        int maxConcurrentUsers,
        long leaderLeaseMs
) {
    private static final long MINUTE_MS = 60_000L;

    public UserSyncConfiguration {
        intervalMs = intervalMs > 0 ? intervalMs : 15 * MINUTE_MS;
        leaseMs = leaseMs > 0 ? leaseMs : 5 * MINUTE_MS;
        workerBatchSize = workerBatchSize > 0 ? workerBatchSize : 25;
        maxConcurrentUsers = maxConcurrentUsers > 0 ? maxConcurrentUsers : 4;
        leaderLeaseMs = leaderLeaseMs > 0 ? leaderLeaseMs : 2 * MINUTE_MS;
    }
}
