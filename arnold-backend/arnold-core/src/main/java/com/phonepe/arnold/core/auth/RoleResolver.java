package com.phonepe.arnold.core.auth;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Maps identity-provider groups and the configured admin list onto Arnold roles.
 */
public class RoleResolver {

    private final AuthConfiguration configuration;

    public RoleResolver(final AuthConfiguration configuration) {
        this.configuration = configuration;
    }

    public Set<String> resolve(final String userId, final String email, final Collection<String> groups) {
        final var roles = new HashSet<String>();
        for (final var group : groups) {
            roles.addAll(configuration.getRoleMappings().getOrDefault(group, Set.of()));
        }
        if (configuration.getAdmins().contains(userId)
                || (email != null && configuration.getAdmins().contains(email))) {
            roles.add(Roles.SUPERADMIN);
        }
        return roles;
    }
}
