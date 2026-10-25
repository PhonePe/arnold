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

import lombok.Data;
import lombok.ToString;

/**
 * Opt-in GitHub Copilot harness. Disabled by default.
 *
 * <p>Copilot token exchange relies on GitHub endpoints that are not part of GitHub's documented public API and
 * may change without notice. Operators must supply their own GitHub OAuth app {@code clientId} and confirm that
 * this use is permitted under their GitHub agreement before enabling it.</p>
 */
@Data
public class CopilotConfiguration {
    private boolean enabled;
    private boolean jobTokenExchangeEnabled;
    private String clientId;
    private String credentialSet = "arnold-copilot-v1";
    @ToString.Exclude
    private String encryptionKey;
}
