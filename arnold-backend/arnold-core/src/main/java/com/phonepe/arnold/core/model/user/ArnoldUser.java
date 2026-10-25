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

package com.phonepe.arnold.core.model.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

import java.util.Map;
import java.util.Set;

public record ArnoldUser(
        String id,
        String email,
        String username,
        String displayName,
        Map<VersionControlProviderType, ProviderIdentity> providerIdentities,
        Set<String> roles,
        long createdAt,
        long updatedAt
) implements Id {

    @JsonCreator
    public ArnoldUser(@JsonProperty("id") final String id,
                      @JsonProperty("email") final String email,
                      @JsonProperty("username") final String username,
                      @JsonProperty("displayName") final String displayName,
                      @JsonProperty("providerIdentities") final Map<VersionControlProviderType, ProviderIdentity> providerIdentities,
                      @JsonProperty("roles") final Set<String> roles,
                      @JsonProperty("createdAt") final long createdAt,
                      @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.displayName = displayName;
        this.providerIdentities = providerIdentities == null ? Map.of() : Map.copyOf(providerIdentities);
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
