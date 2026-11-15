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
package com.phonepe.arnold.core.crypto;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import lombok.extern.slf4j.Slf4j;

/**
 * Encrypts the access and refresh tokens of {@link VersionControlCredential} records before they are stored.
 *
 * <p>Stored values carry a {@code v1:} prefix. Values without it are plaintext written before encryption was
 * introduced; they are returned as-is and encrypted on the next write. The key comes from
 * {@code credentialEncryptionKey}; without it the server starts but storing or reading encrypted credentials fails.</p>
 */
@Slf4j
@Singleton
public class VersionControlCredentialCipher {
    static final String PREFIX = "v1:";

    private final TokenCipher cipher;

    @Inject
    public VersionControlCredentialCipher(final ArnoldCoreConfiguration configuration) {
        this(configuration.getCredentialEncryptionKey());
    }

    public VersionControlCredentialCipher(final String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            log.warn("credentialEncryptionKey is not set; version control credentials cannot be stored");
            this.cipher = null;
        } else {
            this.cipher = new TokenCipher("Version control credential", encodedKey);
        }
    }

    public VersionControlCredential encrypt(final VersionControlCredential credential) {
        if (credential == null) return null;
        return withTokens(credential,
                encryptValue(credential.id() + ":access", credential.accessToken()),
                encryptValue(credential.id() + ":refresh", credential.refreshToken()));
    }

    public VersionControlCredential decrypt(final VersionControlCredential credential) {
        if (credential == null) return null;
        return withTokens(credential,
                decryptValue(credential.id() + ":access", credential.accessToken()),
                decryptValue(credential.id() + ":refresh", credential.refreshToken()));
    }

    private String encryptValue(final String owner, final String value) {
        if (value == null || value.startsWith(PREFIX)) return value;
        return PREFIX + requireCipher().encrypt(owner, value);
    }

    private String decryptValue(final String owner, final String value) {
        if (value == null || !value.startsWith(PREFIX)) return value;
        return requireCipher().decrypt(owner, value.substring(PREFIX.length()));
    }

    private TokenCipher requireCipher() {
        if (cipher == null) {
            throw new IllegalStateException(
                    "Set credentialEncryptionKey (ARNOLD_CREDENTIAL_ENCRYPTION_KEY) to a base64-encoded 32-byte key");
        }
        return cipher;
    }

    private static VersionControlCredential withTokens(final VersionControlCredential c,
                                                       final String accessToken, final String refreshToken) {
        return new VersionControlCredential(c.id(), c.userId(), c.provider(), c.credentialType(), accessToken,
                refreshToken, c.providerUserId(), c.providerUsername(), c.scopes(), c.expiresAt(), c.status(),
                c.createdAt(), c.updatedAt());
    }
}
