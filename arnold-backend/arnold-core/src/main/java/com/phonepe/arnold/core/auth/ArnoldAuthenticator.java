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
