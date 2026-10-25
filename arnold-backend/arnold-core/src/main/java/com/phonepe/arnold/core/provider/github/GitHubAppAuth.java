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

package com.phonepe.arnold.core.provider.github;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;

/**
 * Signs GitHub App JWTs used to call {@code /app} endpoints, e.g. to mint installation tokens.
 */
public class GitHubAppAuth {

    private static final byte[] RSA_ALGORITHM_IDENTIFIER = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};

    private final String appId;
    private final PrivateKey privateKey;
    private final Clock clock;

    public GitHubAppAuth(final long appId, final String privateKeyPem) {
        this(appId, privateKeyPem, Clock.systemUTC());
    }

    GitHubAppAuth(final long appId, final String privateKeyPem, final Clock clock) {
        if (appId <= 0 || privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException("githubConfiguration.appId and privateKey are required");
        }
        this.appId = Long.toString(appId);
        this.privateKey = parse(privateKeyPem);
        this.clock = clock;
    }

    /**
     * @return a JWT valid for nine minutes; GitHub rejects app JWTs that live longer than ten
     */
    public String jwt() {
        final var now = clock.millis();
        final var claims = new JWTClaimsSet.Builder()
                .issuer(appId)
                .issueTime(new Date(now - 60_000))
                .expirationTime(new Date(now + 540_000))
                .build();
        final var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(new RSASSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign GitHub App JWT", e);
        }
        return jwt.serialize();
    }

    static PrivateKey parse(final String value) {
        // Single-line .env values carry newlines as literal "\n".
        final var pem = value.replace("\\n", "\n");
        final var pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        final var body = pem.replaceAll("-----(BEGIN|END) (RSA )?PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            final var der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? wrapPkcs1(der) : der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("githubConfiguration.privateKey is not a valid RSA private key", e);
        }
    }

    /** Wraps a PKCS#1 RSAPrivateKey in a PKCS#8 PrivateKeyInfo structure. */
    private static byte[] wrapPkcs1(final byte[] pkcs1) {
        final var octetString = tlv(0x04, pkcs1);
        final var version = new byte[]{0x02, 0x01, 0x00};
        final var content = new ByteArrayOutputStream();
        content.writeBytes(version);
        content.writeBytes(RSA_ALGORITHM_IDENTIFIER);
        content.writeBytes(octetString);
        return tlv(0x30, content.toByteArray());
    }

    private static byte[] tlv(final int tag, final byte[] value) {
        final var out = new ByteArrayOutputStream();
        out.write(tag);
        final var length = value.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            final var bytes = java.math.BigInteger.valueOf(length).toByteArray();
            final var offset = bytes[0] == 0 ? 1 : 0;
            out.write(0x80 | (bytes.length - offset));
            out.write(bytes, offset, bytes.length - offset);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
