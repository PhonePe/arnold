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

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates services that present one of the configured static API keys.
 */
@Slf4j
public class ApiKeyVerifier implements CredentialVerifier {

    private final List<AuthConfiguration.ApiKey> keys;

    public ApiKeyVerifier(final List<AuthConfiguration.ApiKey> keys) {
        this.keys = keys.stream().filter(key -> key.getKey() != null && !key.getKey().isBlank()).toList();
        if (this.keys.size() != keys.size()) {
            log.warn("Ignoring {} API key(s) with an empty key", keys.size() - this.keys.size());
        }
    }

    @Override
    public Optional<ArnoldPrincipal> verify(final String credential) {
        final var presented = credential.getBytes(StandardCharsets.UTF_8);
        return keys.stream()
                .filter(key -> MessageDigest.isEqual(presented, key.getKey().getBytes(StandardCharsets.UTF_8)))
                .findFirst()
                .map(key -> ArnoldPrincipal.builder()
                        .id("service:" + key.getName())
                        .type(ArnoldPrincipal.Type.SERVICE)
                        .displayName(key.getName())
                        .roles(key.getRoles())
                        .build());
    }
}
