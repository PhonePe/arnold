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

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Arrays;
import java.util.List;

/**
 * Periodic import of every open change request from the repositories Arnold can access.
 *
 * @param gitlabGroups GitLab group ids/paths to import from; entries may be comma separated. When empty, every
 *                     project the service token is a member of is imported.
 */
public record RepositorySyncConfiguration(
        Boolean enabled,
        long intervalSeconds,
        long initialDelaySeconds,
        long leaderLeaseMs,
        int maxReconcilePerRun,
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> gitlabGroups
) {
    public RepositorySyncConfiguration {
        enabled = enabled == null || enabled;
        intervalSeconds = intervalSeconds > 0 ? intervalSeconds : 300;
        initialDelaySeconds = initialDelaySeconds > 0 ? initialDelaySeconds : 30;
        leaderLeaseMs = leaderLeaseMs > 0 ? leaderLeaseMs : 2 * intervalSeconds * 1000;
        maxReconcilePerRun = maxReconcilePerRun > 0 ? maxReconcilePerRun : 200;
        gitlabGroups = gitlabGroups == null ? List.of() : gitlabGroups.stream()
                .filter(value -> value != null)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    public static RepositorySyncConfiguration defaults() {
        return new RepositorySyncConfiguration(null, 0, 0, 0, 0, null);
    }
}
