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

package com.phonepe.arnold.core.config;

import javax.validation.constraints.NotBlank;

public record GitLabSyncConfiguration(
        @NotBlank String webhookSecret,
        long historyWindowMs,
        long watermarkOverlapMs
) {
    private static final long MINUTE_MS = 60_000L;

    public GitLabSyncConfiguration {
        historyWindowMs = historyWindowMs > 0 ? historyWindowMs : 90L * 24 * 60 * MINUTE_MS;
        watermarkOverlapMs = watermarkOverlapMs > 0 ? watermarkOverlapMs : 5 * MINUTE_MS;
    }

    public GitLabSyncConfiguration(final String webhookSecret) {
        this(webhookSecret, 90L * 24 * 60 * MINUTE_MS, 5 * MINUTE_MS);
    }
}
