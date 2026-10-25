package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.github.GitHubIntegration;
import com.phonepe.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.integration.GitHubAuthorizationResponse;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/github-integrations/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class GitHubIntegrationResource {
    private final VersionControlIntegrationService service;
    private final GitHubIntegration github;

    @GET
    @Path("/status")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> status(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.status(VersionControlProviderType.GITHUB, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/oauth/authorization")
    @PermitAll
    public ArnoldResponse<GitHubAuthorizationResponse> authorization(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(new GitHubAuthorizationResponse(github.authorizationUrl(AuthenticatedUser.id(user)),
                github.installationUrl()));
    }

    @GET
    @Path("/oauth/callback")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> oauthCallback(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @NotBlank @QueryParam("code") final String code,
            @NotBlank @QueryParam("state") final String state) {
        return ArnoldResponse.ok(github.connect(AuthenticatedUser.id(user), code, state));
    }

    @DELETE
    @Path("/connection")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> disconnect(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.disconnect(VersionControlProviderType.GITHUB, AuthenticatedUser.id(user)));
    }
}
