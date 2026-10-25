package com.phonepe.arnold.core.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies JWTs from the configured issuers. An issuer is trusted either through its JWKS endpoint
 * (OIDC providers) or through a shared HMAC secret (e.g. the Arnold UI server minting session tokens).
 */
@Slf4j
public class JwtVerifier implements CredentialVerifier {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<JWSAlgorithm> ASYMMETRIC = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512);
    private static final Set<JWSAlgorithm> SYMMETRIC = Set.of(JWSAlgorithm.HS256, JWSAlgorithm.HS384, JWSAlgorithm.HS512);

    private final AuthConfiguration.Jwt configuration;
    private final RoleResolver roleResolver;
    private final Map<String, ConfigurableJWTProcessor<SecurityContext>> processors = new HashMap<>();

    public JwtVerifier(final AuthConfiguration.Jwt configuration, final RoleResolver roleResolver) {
        this.configuration = configuration;
        this.roleResolver = roleResolver;
        for (final var issuer : configuration.getIssuers()) {
            processors.put(issuer.getIssuer(), processor(issuer));
        }
    }

    @Override
    public Optional<ArnoldPrincipal> verify(final String credential) {
        if (processors.isEmpty() || credential.chars().filter(c -> c == '.').count() != 2) {
            return Optional.empty();
        }
        final String issuer;
        try {
            issuer = SignedJWT.parse(credential).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            return Optional.empty();
        }
        final var processor = issuer == null ? null : processors.get(issuer);
        if (processor == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(toPrincipal(processor.process(credential, null)));
        } catch (Exception e) {
            log.debug("Rejected token from issuer {}: {}", issuer, e.getMessage());
            return Optional.empty();
        }
    }

    private ArnoldPrincipal toPrincipal(final JWTClaimsSet claims) throws ParseException {
        final var userId = claims.getStringClaim(configuration.getUserIdClaim());
        if (userId == null || userId.isBlank()) {
            throw new ParseException("Missing user id claim", 0);
        }
        final var email = claims.getStringClaim(configuration.getEmailClaim());
        final var groups = groups(claims.getClaim(configuration.getRolesClaim()));
        return ArnoldPrincipal.builder()
                .id(userId)
                .type(ArnoldPrincipal.Type.USER)
                .email(email)
                .displayName(claims.getStringClaim(configuration.getNameClaim()))
                .roles(roleResolver.resolve(userId, email, groups))
                .build();
    }

    private static Collection<String> groups(final Object claim) {
        if (claim instanceof Collection<?> values) {
            final var groups = new HashSet<String>();
            values.forEach(value -> groups.add(String.valueOf(value)));
            return groups;
        }
        if (claim instanceof String value) {
            return List.of(value.split("[,\\s]+"));
        }
        return List.of();
    }

    private ConfigurableJWTProcessor<SecurityContext> processor(final AuthConfiguration.Issuer issuer) {
        final var hasSecret = issuer.getSecret() != null && !issuer.getSecret().isBlank();
        final var hasJwks = issuer.getJwksUrl() != null && !issuer.getJwksUrl().isBlank();
        if (hasSecret == hasJwks) {
            throw new IllegalArgumentException("Issuer " + issuer.getIssuer() + " must set exactly one of secret or jwksUrl");
        }
        final JWKSource<SecurityContext> keySource;
        final Set<JWSAlgorithm> algorithms;
        if (hasSecret) {
            final var secret = issuer.getSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException("Secret for issuer " + issuer.getIssuer()
                        + " must be at least " + MIN_SECRET_BYTES + " bytes");
            }
            keySource = new ImmutableSecret<>(secret);
            algorithms = SYMMETRIC;
        } else {
            try {
                keySource = JWKSourceBuilder.create(URI.create(issuer.getJwksUrl()).toURL()).build();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Invalid jwksUrl for issuer " + issuer.getIssuer(), e);
            }
            algorithms = ASYMMETRIC;
        }
        final var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, keySource));
        final var verifier = new DefaultJWTClaimsVerifier<SecurityContext>(
                issuer.getAudience() == null || issuer.getAudience().isBlank() ? null : issuer.getAudience(),
                new JWTClaimsSet.Builder().issuer(issuer.getIssuer()).build(),
                Set.of("sub", "exp"));
        verifier.setMaxClockSkew((int) configuration.getClockSkewSeconds());
        processor.setJWTClaimsSetVerifier(verifier);
        return processor;
    }
}
