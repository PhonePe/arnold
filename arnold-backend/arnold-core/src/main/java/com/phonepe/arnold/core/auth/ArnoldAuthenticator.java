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

import io.dropwizard.auth.Authenticator;

import java.util.List;
import java.util.Optional;

/**
 * Delegates bearer credentials to the registered {@link CredentialVerifier}s.
 */
public class ArnoldAuthenticator implements Authenticator<String, ArnoldPrincipal> {

    private final List<CredentialVerifier> verifiers;

    public ArnoldAuthenticator(final List<CredentialVerifier> verifiers) {
        this.verifiers = List.copyOf(verifiers);
    }

    @Override
    public Optional<ArnoldPrincipal> authenticate(final String credential) {
        if (credential == null || credential.isBlank()) {
            return Optional.empty();
        }
        for (final var verifier : verifiers) {
            final var principal = verifier.verify(credential);
            if (principal.isPresent()) {
                return principal;
            }
        }
        return Optional.empty();
    }
}
