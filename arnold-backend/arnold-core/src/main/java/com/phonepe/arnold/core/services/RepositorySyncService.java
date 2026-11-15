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
package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.sync.RepositorySyncOverviewResponse;

public interface RepositorySyncService {
    String LEADER_ELECTION_NAME = "repository-sync";

    RepositorySyncOverviewResponse status();

    /**
     * Starts a run in the background.
     *
     * @return false when a run is already in progress on this node
     */
    boolean trigger();

    /**
     * Runs every available provider on the calling thread; no-op when a run is already in progress on this node.
     */
    void runOnce();
}
