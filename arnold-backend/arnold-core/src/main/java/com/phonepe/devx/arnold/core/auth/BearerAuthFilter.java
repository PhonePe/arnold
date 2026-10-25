package com.phonepe.devx.arnold.core.auth;

import io.dropwizard.auth.AuthFilter;
import ru.vyarus.dropwizard.guice.module.installer.scanner.InvisibleForScanner;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import java.util.Locale;

/**
 * Authenticates {@code Authorization: Bearer <credential>} headers. In dev mode, requests without a
 * credential are authenticated as the configured development user.
 */
@InvisibleForScanner
@Priority(Priorities.AUTHENTICATION)
public class BearerAuthFilter extends AuthFilter<String, ArnoldPrincipal> {

    private static final String BEARER = "bearer ";
    private static final String DEV_CREDENTIAL = "\0dev";

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final var credential = credential(requestContext.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!authenticate(requestContext, credential, "Bearer")) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }

    private static String credential(final String header) {
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
            return DEV_CREDENTIAL;
        }
        return header.substring(BEARER.length()).trim();
    }

    static boolean isDevCredential(final String credential) {
        return DEV_CREDENTIAL.equals(credential);
    }

    public static class Builder extends AuthFilterBuilder<String, ArnoldPrincipal, BearerAuthFilter> {
        @Override
        protected BearerAuthFilter newInstance() {
            return new BearerAuthFilter();
        }
    }
}
