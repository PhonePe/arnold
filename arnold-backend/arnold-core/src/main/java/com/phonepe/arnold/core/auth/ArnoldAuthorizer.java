package com.phonepe.arnold.core.auth;

import io.dropwizard.auth.Authorizer;

/**
 * Grants a role when the principal holds it; {@link Roles#SUPERADMIN} implies every role.
 */
public class ArnoldAuthorizer implements Authorizer<ArnoldPrincipal> {

    @Override
    public boolean authorize(final ArnoldPrincipal principal, final String role) {
        return principal.hasRole(role) || principal.hasRole(Roles.SUPERADMIN);
    }
}
