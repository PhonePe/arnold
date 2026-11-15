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

package com.phonepe.arnold.core.auth;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Authenticates requests that carry no credential as the configured development user.
 */
@Slf4j
public class DevVerifier implements CredentialVerifier {

    private final ArnoldPrincipal principal;

    public DevVerifier(final AuthConfiguration.Dev dev) {
        this.principal = ArnoldPrincipal.builder()
                .id(dev.getUserId())
                .type(ArnoldPrincipal.Type.USER)
                .email(dev.getEmail())
                .displayName(dev.getDisplayName())
                .roles(dev.getRoles())
                .build();
        log.warn("Development authentication is enabled; unauthenticated requests act as user '{}'", dev.getUserId());
    }

    @Override
    public Optional<ArnoldPrincipal> verify(final String credential) {
        return BearerAuthFilter.isDevCredential(credential) ? Optional.of(principal) : Optional.empty();
    }
}
