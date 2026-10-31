package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/merge-queues/v1")
@Tag(name = "Merge Queue APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
@AllArgsConstructor(onConstructor_ = @Inject)
public class MergeQueueResource {
    private final MergeQueueService mergeQueueService;

    @GET
    @Path("/mine")
    @PermitAll
    @Operation(description = "Get the current user's merge queue")
    public ArnoldResponse<MergeQueueResponse> get(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(mergeQueueService.get(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/mine/items")
    @PermitAll
    @Operation(description = "Add an item to the current user's merge queue")
    public ArnoldResponse<MergeQueueResponse> add(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Valid final AddMergeQueueItemRequest request) {
        return ArnoldResponse.ok(mergeQueueService.add(AuthenticatedUser.id(user), request));
    }

    @DELETE
    @Path("/mine/items/{itemId}")
    @PermitAll
    @Operation(description = "Remove an item from the current user's merge queue")
    public ArnoldResponse<MergeQueueResponse> remove(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @PathParam("itemId") final String itemId) {
        return ArnoldResponse.ok(mergeQueueService.remove(AuthenticatedUser.id(user), itemId));
    }

    @POST
    @Path("/mine/reorder")
    @PermitAll
    @Operation(description = "Reorder the current user's merge queue")
    public ArnoldResponse<MergeQueueResponse> reorder(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Valid final ReorderMergeQueueRequest request) {
        return ArnoldResponse.ok(mergeQueueService.reorder(AuthenticatedUser.id(user), request));
    }
}
