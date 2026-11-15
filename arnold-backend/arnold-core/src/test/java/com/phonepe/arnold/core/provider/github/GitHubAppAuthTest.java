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

package com.phonepe.arnold.core.provider.github;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAppAuthTest {

    @Test
    void signsJwtWithPkcs8AndPkcs1Keys() throws Exception {
        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final var pair = generator.generateKeyPair();
        final var pkcs8 = pair.getPrivate().getEncoded();
        // PKCS#8 for RSA = fixed 26-byte header followed by the PKCS#1 key.
        final var pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);

        for (final var pem : new String[]{
                pem("PRIVATE KEY", pkcs8), pem("RSA PRIVATE KEY", pkcs1)}) {
            final var jwt = SignedJWT.parse(new GitHubAppAuth(123, pem).jwt());
            assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic())));
            assertEquals("123", jwt.getJWTClaimsSet().getIssuer());
            final var lifetime = jwt.getJWTClaimsSet().getExpirationTime().getTime()
                    - jwt.getJWTClaimsSet().getIssueTime().getTime();
            assertTrue(lifetime <= 10 * 60_000);
        }
    }

    @Test
    void rejectsMissingOrInvalidKeys() {
        assertThrows(IllegalStateException.class, () -> new GitHubAppAuth(0, "x"));
        assertThrows(IllegalStateException.class, () -> new GitHubAppAuth(1, " "));
        assertThrows(IllegalStateException.class, () -> new GitHubAppAuth(1, pem("PRIVATE KEY", new byte[]{1, 2})));
    }

    private static String pem(final String type, final byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
