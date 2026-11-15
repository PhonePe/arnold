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

package com.phonepe.arnold.core.model.vcs;

import com.livetheoogway.crudstore.core.Id;

import java.util.Set;

public record VersionControlCredential(
        String id,
        String userId,
        VersionControlProviderType provider,
        String credentialType,
        String accessToken,
        String refreshToken,
        String providerUserId,
        String providerUsername,
        Set<String> scopes,
        Long expiresAt,
        String status,
        long createdAt,
        long updatedAt
) implements Id {
    public VersionControlCredential {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public static String key(final VersionControlProviderType provider, final String userId) {
        return provider.name().toLowerCase() + ":" + userId;
    }
}
