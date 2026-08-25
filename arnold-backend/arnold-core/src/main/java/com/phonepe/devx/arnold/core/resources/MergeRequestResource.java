package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.ResolveMergeRequestRequest;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.olympus.im.client.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/merge-requests/v1")
@Tag(name = "Merge Request APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
public class MergeRequestResource {
    private final ChangeRequestService mergeRequestService;
    private final UserMergeRequestService userMergeRequestService;

    @Inject
    public MergeRequestResource(final ChangeRequestService mergeRequestService,
                                final UserMergeRequestService userMergeRequestService) {
        this.mergeRequestService = mergeRequestService;
        this.userMergeRequestService = userMergeRequestService;
    }

    public MergeRequestResource(final ChangeRequestService mergeRequestService) {
        this(mergeRequestService, null);
    }

    @GET
    @Path("/mine")
    @PermitAll
    @Operation(description = "List the authenticated user's synced open merge requests")
    public ArnoldResponse<UserMergeRequestPageResponse> mine(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @QueryParam("role") @DefaultValue("all") final String role,
            @QueryParam("limit") @DefaultValue("50") final int limit,
            @QueryParam("cursor") final String cursor) {
        return ArnoldResponse.ok(userMergeRequestService.list(AuthenticatedUser.id(user), role, limit, cursor));
    }

    @POST
    @Path("/resolve")
    @PermitAll
    @Operation(description = "Resolve a GitLab merge request URL")
    public ArnoldResponse<MergeRequestResponse> resolve(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Valid final ResolveMergeRequestRequest request) {
        return ArnoldResponse.ok(mergeRequestService.resolve(request.getUrl(), AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}")
    @PermitAll
    @Operation(description = "Get a merge request")
    public ArnoldResponse<MergeRequestResponse> get(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid) {
        return ArnoldResponse.ok(mergeRequestService.get(projectId, iid, AuthenticatedUser.id(user)));
    }
}
