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
package com.phonepe.arnold.core.model.sync;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositorySyncState(
        VersionControlProviderType provider,
        SyncStatus status,
        long lastAttemptAt,
        long lastSucceededAt,
        int repositories,
        int changeRequests,
        String failure
) {
    @JsonCreator
    public RepositorySyncState(@JsonProperty("provider") final VersionControlProviderType provider,
                               @JsonProperty("status") final SyncStatus status,
                               @JsonProperty("lastAttemptAt") final long lastAttemptAt,
                               @JsonProperty("lastSucceededAt") final long lastSucceededAt,
                               @JsonProperty("repositories") final int repositories,
                               @JsonProperty("changeRequests") final int changeRequests,
                               @JsonProperty("failure") final String failure) {
        this.provider = provider;
        this.status = status;
        this.lastAttemptAt = lastAttemptAt;
        this.lastSucceededAt = lastSucceededAt;
        this.repositories = repositories;
        this.changeRequests = changeRequests;
        this.failure = failure;
    }

    public static String key(final VersionControlProviderType provider) {
        return "repository-sync:" + provider.name().toLowerCase(Locale.ROOT);
    }
}
