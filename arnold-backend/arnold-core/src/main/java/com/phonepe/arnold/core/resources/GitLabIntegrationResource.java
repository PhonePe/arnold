/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.model.vcs.VersionControlConnection;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.core.services.SetupService;
import com.phonepe.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.integration.ConnectGitLabRequest;
import com.phonepe.arnold.models.integration.GitLabOAuthAuthorizationResponse;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.AllArgsConstructor;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/gitlab-integrations/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class GitLabIntegrationResource {
    private final VersionControlIntegrationService service;
    private final GitLabOAuthService oauthService;
    private final SetupService setup;

    @GET
    @Path("/status")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> status(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        if (!setup.gitLabConfigured()) {
            return ArnoldResponse.ok(new VersionControlIntegrationResponse(false, VersionControlProviderType.GITLAB.name(),
                    null, null, null, java.util.Set.of(), null, "unconfigured"));
        }
        return ArnoldResponse.ok(service.status(VersionControlProviderType.GITLAB, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/oauth/authorization")
    @PermitAll
    public ArnoldResponse<GitLabOAuthAuthorizationResponse> authorization(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        final var authorizationUrl = oauthService.authorizationUrl(AuthenticatedUser.id(user));
        return ArnoldResponse.ok(new GitLabOAuthAuthorizationResponse(authorizationUrl));
    }

    @GET
    @Path("/oauth/callback")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> oauthCallback(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @NotBlank @QueryParam("code") final String code,
            @NotBlank @QueryParam("state") final String state) {
        return ArnoldResponse.ok(oauthService.connect(AuthenticatedUser.id(user), code, state));
    }

    @POST
    @Path("/connect")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> connect(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @Valid final ConnectGitLabRequest request) {
        final var connection = new VersionControlConnection(request.getCredentialType().name(),
                request.getSecretReference(), request.getGitlabUserId() == null ? null : request.getGitlabUserId().toString(),
                request.getGitlabUsername(), request.getScopes(), request.getExpiresAt());
        return ArnoldResponse.ok(service.connect(VersionControlProviderType.GITLAB, AuthenticatedUser.id(user), connection));
    }

    @DELETE
    @Path("/connection")
    @PermitAll
    public ArnoldResponse<VersionControlIntegrationResponse> disconnect(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.disconnect(VersionControlProviderType.GITLAB, AuthenticatedUser.id(user)));
    }
}
