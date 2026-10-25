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

package com.phonepe.arnold.core.model.copilot;

import com.livetheoogway.crudstore.core.Id;

public record CopilotAccount(String id, Credential credential, Attempt attempt, String operationId, long busyUntil)
        implements Id {
    public record Credential(String access, String refresh, Long expiresAt, Long refreshExpiresAt,
                             String githubUserId, String username, String status, long checkedAt, String connectionId) { }

    public record Attempt(String id, String deviceCode, String userCode, String verificationUri,
                          long expiresAt, int interval, long nextPollAt, String status) { }

    public CopilotAccount withAttempt(final Attempt value) {
        return new CopilotAccount(id, credential, value, null, 0);
    }

    public CopilotAccount withCredential(final Credential value) {
        return new CopilotAccount(id, value, attempt, null, 0);
    }
}
