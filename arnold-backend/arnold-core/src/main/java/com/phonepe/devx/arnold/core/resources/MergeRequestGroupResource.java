package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.MergeRequestGroupService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.devx.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/merge-request-groups/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class MergeRequestGroupResource {
    private final MergeRequestGroupService service;

    @GET
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupCollectionResponse> get(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.get(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupCollectionResponse> create(
            @Auth final ServiceUserPrincipal user,
            @Valid final CreateMergeRequestGroupRequest request) {
        return ArnoldResponse.ok(service.create(AuthenticatedUser.id(user), request));
    }

    @PUT
    @Path("/mine/{groupId}")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupCollectionResponse> update(
            @Auth final ServiceUserPrincipal user,
            @PathParam("groupId") final String groupId,
            @Valid final UpdateMergeRequestGroupRequest request) {
        return ArnoldResponse.ok(service.update(AuthenticatedUser.id(user), groupId, request));
    }

    @DELETE
    @Path("/mine/{groupId}")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupCollectionResponse> delete(
            @Auth final ServiceUserPrincipal user,
            @PathParam("groupId") final String groupId,
            @PositiveOrZero @QueryParam("version") final long version) {
        return ArnoldResponse.ok(service.delete(AuthenticatedUser.id(user), groupId, version));
    }
}
