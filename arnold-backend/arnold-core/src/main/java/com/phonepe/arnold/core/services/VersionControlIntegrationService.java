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

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.model.vcs.VersionControlConnection;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;

public interface VersionControlIntegrationService {
    VersionControlIntegrationResponse status(VersionControlProviderType provider, String userId);

    VersionControlIntegrationResponse connect(VersionControlProviderType provider, String userId,
                                              VersionControlConnection connection);

    VersionControlIntegrationResponse disconnect(VersionControlProviderType provider, String userId);
}
