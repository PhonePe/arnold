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

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.sync.UserSyncStatusResponse;

public interface UserSyncService {
    UserSyncStatusResponse ensureScheduled(VersionControlProviderType provider, String userId);

    UserSyncStatusResponse retry(VersionControlProviderType provider, String userId);

    UserSyncStatusResponse status(VersionControlProviderType provider, String userId);

    void cancel(VersionControlProviderType provider, String userId);

    void process(UserSyncState dueState, String workerId);
}
