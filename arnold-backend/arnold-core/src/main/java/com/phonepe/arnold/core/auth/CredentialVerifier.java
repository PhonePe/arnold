package com.phonepe.arnold.core.auth;

import java.util.Optional;

/**
 * Extension point that turns a bearer credential into a principal.
 * Verifiers are consulted in order; the first one that recognises the credential wins.
 * Additional verifiers can be contributed with a Guice {@code Multibinder<CredentialVerifier>}.
 */
public interface CredentialVerifier {

    /**
     * @return the principal, or empty when this verifier does not recognise the credential
     */
    Optional<ArnoldPrincipal> verify(String credential);
}
