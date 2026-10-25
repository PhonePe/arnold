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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static AuthConfiguration configuration() {
        final var issuer = new AuthConfiguration.Issuer();
        issuer.setIssuer("arnold-ui");
        issuer.setAudience("arnold");
        issuer.setSecret(SECRET);
        final var config = new AuthConfiguration();
        config.getJwt().setIssuers(List.of(issuer));
        config.setRoleMappings(Map.of("reviewers", Set.of(Roles.REVIEW_FEEDBACK_READ)));
        config.setAdmins(Set.of("admin@example.com"));
        final var key = new AuthConfiguration.ApiKey();
        key.setName("runner");
        key.setKey("runner-key");
        key.setRoles(Set.of(Roles.ANALYSIS_CALLBACK_WRITE));
        config.setApiKeys(List.of(key));
        return config;
    }

    private static String token(final String issuer, final String audience, final String secret,
                                final String email, final long expiresInMs) throws Exception {
        final var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("user-1")
                .claim("email", email)
                .claim("name", "Jane Doe")
                .claim("groups", List.of("reviewers"))
                .expirationTime(new Date(System.currentTimeMillis() + expiresInMs))
                .build();
        final var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    private static JwtVerifier jwtVerifier(final AuthConfiguration config) {
        return new JwtVerifier(config.getJwt(), new RoleResolver(config));
    }

    @Test
    void jwtFromTrustedIssuerMapsClaimsAndRoles() throws Exception {
        final var config = configuration();
        final var principal = jwtVerifier(config)
                .verify(token("arnold-ui", "arnold", SECRET, "admin@example.com", 60_000))
                .orElseThrow();

        assertEquals("user-1", principal.getId());
        assertEquals(ArnoldPrincipal.Type.USER, principal.getType());
        assertEquals("Jane Doe", principal.getDisplayName());
        assertEquals(Set.of(Roles.REVIEW_FEEDBACK_READ, Roles.SUPERADMIN), principal.getRoles());
    }

    @Test
    void jwtIsRejectedWhenSignatureAudienceIssuerOrExpiryIsWrong() throws Exception {
        final var verifier = jwtVerifier(configuration());
        final var otherSecret = "fedcba9876543210fedcba9876543210";

        assertTrue(verifier.verify(token("arnold-ui", "arnold", otherSecret, "a@example.com", 60_000)).isEmpty());
        assertTrue(verifier.verify(token("arnold-ui", "other", SECRET, "a@example.com", 60_000)).isEmpty());
        assertTrue(verifier.verify(token("unknown", "arnold", SECRET, "a@example.com", 60_000)).isEmpty());
        assertTrue(verifier.verify(token("arnold-ui", "arnold", SECRET, "a@example.com", -600_000)).isEmpty());
        assertTrue(verifier.verify("not-a-jwt").isEmpty());
    }

    @Test
    void issuerRequiresExactlyOneKeySourceAndAStrongSecret() {
        final var config = configuration();
        config.getJwt().getIssuers().get(0).setJwksUrl("https://idp.example.com/jwks");
        assertThrows(IllegalArgumentException.class, () -> jwtVerifier(config));

        final var weak = configuration();
        weak.getJwt().getIssuers().get(0).setSecret("short");
        assertThrows(IllegalArgumentException.class, () -> jwtVerifier(weak));
    }

    @Test
    void apiKeyAuthenticatesServicesWithConfiguredRoles() {
        final var verifier = new ApiKeyVerifier(configuration().getApiKeys());

        final var principal = verifier.verify("runner-key").orElseThrow();

        assertEquals("service:runner", principal.getId());
        assertEquals(ArnoldPrincipal.Type.SERVICE, principal.getType());
        assertTrue(principal.hasRole(Roles.ANALYSIS_CALLBACK_WRITE));
        assertTrue(verifier.verify("wrong").isEmpty());
    }

    @Test
    void authenticatorFallsBackToDevUserOnlyWhenNoCredentialIsPresented() {
        final var dev = new AuthConfiguration.Dev();
        final var authenticator = new ArnoldAuthenticator(List.of(
                new ApiKeyVerifier(configuration().getApiKeys()), new DevVerifier(dev)));

        assertEquals("dev", authenticator.authenticate("\0dev").orElseThrow().getId());
        assertTrue(authenticator.authenticate("garbage").isEmpty());
        assertEquals("service:runner", authenticator.authenticate("runner-key").orElseThrow().getId());
    }

    @Test
    void superadminImpliesEveryRole() {
        final var authorizer = new ArnoldAuthorizer();
        final var admin = ArnoldPrincipal.builder().id("a").type(ArnoldPrincipal.Type.USER)
                .role(Roles.SUPERADMIN).build();
        final var reader = ArnoldPrincipal.builder().id("r").type(ArnoldPrincipal.Type.USER)
                .role(Roles.REVIEW_FEEDBACK_READ).build();

        assertTrue(authorizer.authorize(admin, Roles.BACKFILL_REVIEW_FEEDBACK));
        assertTrue(authorizer.authorize(reader, Roles.REVIEW_FEEDBACK_READ));
        assertFalse(authorizer.authorize(reader, Roles.REVIEW_FEEDBACK_WRITE));
    }
}
