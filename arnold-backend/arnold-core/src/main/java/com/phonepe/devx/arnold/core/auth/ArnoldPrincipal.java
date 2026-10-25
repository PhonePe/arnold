package com.phonepe.devx.arnold.core.auth;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.security.Principal;
import java.util.Set;

/**
 * The authenticated caller of an Arnold API: either a human user or a service (runner, webhook relay, etc).
 */
@Value
@Builder(toBuilder = true)
public class ArnoldPrincipal implements Principal {

    public enum Type {
        USER,
        SERVICE
    }

    @NonNull
    String id;
    @NonNull
    Type type;
    String email;
    String displayName;
    @Singular
    Set<String> roles;

    @Override
    public String getName() {
        return id;
    }

    public boolean hasRole(final String role) {
        return roles.contains(role);
    }
}
