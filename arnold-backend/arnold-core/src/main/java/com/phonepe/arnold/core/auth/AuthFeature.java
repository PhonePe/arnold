package com.phonepe.arnold.core.auth;

import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.inject.Inject;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.util.ArrayList;
import java.util.Set;

/**
 * Installs bearer authentication and {@code @RolesAllowed} authorization on Jersey resources.
 */
public class AuthFeature implements Feature {

    private final AuthConfiguration configuration;
    private final Set<CredentialVerifier> extraVerifiers;

    @Inject
    public AuthFeature(final AuthConfiguration configuration, final Set<CredentialVerifier> extraVerifiers) {
        this.configuration = configuration;
        this.extraVerifiers = extraVerifiers;
    }

    @Override
    public boolean configure(final FeatureContext context) {
        final var verifiers = new ArrayList<CredentialVerifier>();
        verifiers.add(new ApiKeyVerifier(configuration.getApiKeys()));
        verifiers.add(new JwtVerifier(configuration.getJwt(), new RoleResolver(configuration)));
        verifiers.addAll(extraVerifiers);
        if (configuration.getDev().isEnabled()) {
            verifiers.add(new DevVerifier(configuration.getDev()));
        }
        context.register(new AuthDynamicFeature(new BearerAuthFilter.Builder()
                .setAuthenticator(new ArnoldAuthenticator(verifiers))
                .setAuthorizer(new ArnoldAuthorizer())
                .setPrefix("Bearer")
                .setRealm("arnold")
                .buildAuthFilter()));
        context.register(RolesAllowedDynamicFeature.class);
        context.register(new AuthValueFactoryProvider.Binder<>(ArnoldPrincipal.class));
        return true;
    }
}
