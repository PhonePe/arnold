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

package com.phonepe.arnold.core.auth;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.security.Principal;
import java.util.Set;

/**
 * The authenticated caller of an Arnold API: either a human user or a service (runner, webhook relay, etc).
 */
@Value
@Builder(toBuilder = true)
public class ArnoldPrincipal implements Principal {

    public enum Type {
        USER,
        SERVICE
    }

    @NonNull
    String id;
    @NonNull
    Type type;
    String email;
    String displayName;
    @Singular
    Set<String> roles;

    @Override
    public String getName() {
        return id;
    }

    public boolean hasRole(final String role) {
        return roles.contains(role);
    }
}
