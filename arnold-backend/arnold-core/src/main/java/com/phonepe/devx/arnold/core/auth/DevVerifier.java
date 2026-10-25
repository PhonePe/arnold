package com.phonepe.devx.arnold.core.auth;

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
