package com.phonepe.devx.arnold.core.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authentication settings.
 *
 * <ul>
 *     <li>{@code jwt.issuers}: trusted token issuers (OIDC providers via JWKS, or the Arnold UI via a shared secret).</li>
 *     <li>{@code apiKeys}: static keys for services such as review runners.</li>
 *     <li>{@code dev}: authenticates every request as a fixed user. Never enable outside local development.</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthConfiguration {

    @Valid
    private Jwt jwt = new Jwt();

    @Valid
    private List<ApiKey> apiKeys = List.of();

    @Valid
    private Dev dev = new Dev();

    /**
     * Grants roles to users whose role/group claim contains the key.
     */
    private Map<String, Set<String>> roleMappings = Map.of();

    /**
     * Emails or user ids that are granted {@link Roles#SUPERADMIN}.
     */
    private Set<String> admins = Set.of();

    @Data
    public static class Jwt {
        @Valid
        private List<Issuer> issuers = List.of();
        private String userIdClaim = "sub";
        private String emailClaim = "email";
        private String nameClaim = "name";
        private String rolesClaim = "groups";
        private long clockSkewSeconds = 60;
    }

    @Data
    @ToString(exclude = "secret")
    public static class Issuer {
        @NotBlank
        private String issuer;
        private String audience;
        private String jwksUrl;
        private String secret;
    }

    @Data
    @ToString(exclude = "key")
    public static class ApiKey {
        @NotBlank
        private String name;
        private String key;
        private Set<String> roles = Set.of();
    }

    @Data
    public static class Dev {
        private boolean enabled;
        private String userId = "dev";
        private String email = "dev@localhost";
        private String displayName = "Developer";
        private Set<String> roles = Set.of(Roles.SUPERADMIN);
    }
}
