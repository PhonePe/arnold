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

import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VersionControlCredentialCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static VersionControlCredential credential(final String id, final String access, final String refresh) {
        return new VersionControlCredential(id, "alice", VersionControlProviderType.values()[0], "oauth", access,
                refresh, "1", "alice", Set.of("api"), 1L, "ACTIVE", 1L, 2L);
    }

    @Test
    void testEncrypt_shouldHideTokensAndRoundTrip_whenKeyIsSet() {
        final var cipher = new VersionControlCredentialCipher(KEY);
        final var encrypted = cipher.encrypt(credential("gitlab:alice", "glpat-secret", "refresh-secret"));

        assertTrue(encrypted.accessToken().startsWith(VersionControlCredentialCipher.PREFIX));
        assertFalse(encrypted.accessToken().contains("glpat-secret"));
        assertFalse(encrypted.refreshToken().contains("refresh-secret"));
        assertEquals("alice", encrypted.providerUsername());
        assertEquals(encrypted, cipher.encrypt(encrypted));

        final var decrypted = cipher.decrypt(encrypted);
        assertEquals("glpat-secret", decrypted.accessToken());
        assertEquals("refresh-secret", decrypted.refreshToken());
    }

    @Test
    void testDecrypt_shouldFail_whenCiphertextIsMovedOrTampered() {
        final var cipher = new VersionControlCredentialCipher(KEY);
        final var encrypted = cipher.encrypt(credential("gitlab:alice", "glpat-secret", "refresh-secret"));

        final var moved = credential("gitlab:bob", encrypted.accessToken(), null);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(moved));
        final var swapped = credential("gitlab:alice", encrypted.refreshToken(), encrypted.accessToken());
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(swapped));
        final var tampered = credential("gitlab:alice", encrypted.accessToken().substring(0, 10) + "AAAA"
                + encrypted.accessToken().substring(14), null);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void testDecrypt_shouldReturnLegacyPlaintext_whenValueHasNoPrefix() {
        final var cipher = new VersionControlCredentialCipher((String) null);
        final var legacy = credential("gitlab:alice", "glpat-legacy", null);
        assertEquals(legacy, cipher.decrypt(legacy));
    }

    @Test
    void testEncrypt_shouldFail_whenKeyIsMissing() {
        final var cipher = new VersionControlCredentialCipher("");
        assertThrows(IllegalStateException.class, () -> cipher.encrypt(credential("gitlab:alice", "glpat", null)));
        assertThrows(IllegalArgumentException.class, () -> new VersionControlCredentialCipher("not-a-key"));
    }
}
