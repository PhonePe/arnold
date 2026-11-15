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
package com.phonepe.arnold.models.dashboard;

import com.phonepe.arnold.models.integration.CopilotConnectionResponse;

import java.util.List;

/**
 * What this deployment offers and what the signed-in user has connected, so the UI can guide first-time setup.
 *
 * @param forges     every source-control provider the deployment knows about, configured or not
 * @param copilot    the user's Copilot connection; {@code enabled=false} when the deployment does not offer Copilot
 * @param runnerMode where review jobs execute: {@code LOCAL}, {@code GITLAB} or {@code GITHUB}
 */
public record SetupResponse(List<Forge> forges, CopilotConnectionResponse copilot, String runnerMode) {

    /**
     * @param provider    {@code GITHUB} or {@code GITLAB}
     * @param configured  the deployment has credentials for this provider, so users can connect it
     * @param connected   the signed-in user has a usable credential for it
     * @param username    the user's handle on the provider, when connected
     * @param installUrl  where to install the GitHub App on repositories; {@code null} for other providers
     * @param status      {@code unconfigured}, {@code disconnected}, {@code connected} or {@code reconnect_required}
     *                    when the provider no longer accepts the stored credential
     */
    public record Forge(String provider, boolean configured, boolean connected, String username,
                       String installUrl, String status) {
    }
}
