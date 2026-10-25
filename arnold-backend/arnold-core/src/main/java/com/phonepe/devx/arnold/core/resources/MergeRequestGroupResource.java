package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.MergeRequestGroupService;
import com.phonepe.devx.arnold.core.services.GroupAnalysisService;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupCollectionResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeRequestGroupResponse;
import com.phonepe.devx.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/merge-request-groups/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class MergeRequestGroupResource {
    private final MergeRequestGroupService service;
    private final GroupAnalysisService analyses;

    @POST
    @Path("/{groupId}/analyses")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public ArnoldResponse<GroupAnalysisResponse> analyze(
            @Auth final ArnoldPrincipal user, @PathParam("groupId") final String groupId,
            @Valid @javax.validation.constraints.NotNull final GroupAnalysisResponse.StartRequest request) {
        return ArnoldResponse.ok(analyses.start(AuthenticatedUser.id(user), groupId, request));
    }

    @GET
    @PermitAll
    public ArnoldResponse<MergeRequestGroupCollectionResponse> list(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.list(AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{groupId}")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupResponse> get(@PathParam("groupId") final String groupId) {
        return ArnoldResponse.ok(service.get(groupId));
    }

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public ArnoldResponse<MergeRequestGroupCollectionResponse> create(
            @Auth final ArnoldPrincipal user,
            @Valid final CreateMergeRequestGroupRequest request) {
        return ArnoldResponse.ok(service.create(AuthenticatedUser.id(user), request));
    }

    @PUT
    @Path("/{groupId}")
    @PermitAll
    public ArnoldResponse<MergeRequestGroupResponse> update(
            @Auth final ArnoldPrincipal user,
            @PathParam("groupId") final String groupId,
            @Valid final UpdateMergeRequestGroupRequest request) {
        return ArnoldResponse.ok(service.update(AuthenticatedUser.id(user), groupId, request));
    }

    @DELETE
    @Path("/{groupId}")
    @PermitAll
    public ArnoldResponse<Void> delete(
            @Auth final ArnoldPrincipal user,
            @PathParam("groupId") final String groupId,
            @PositiveOrZero @QueryParam("version") final long version) {
        service.delete(AuthenticatedUser.id(user), groupId, version);
        return ArnoldResponse.ok(null);
    }

    @GET
    @Path("/mine")
    @Deprecated
    public ArnoldResponse<MergeRequestGroupCollectionResponse> mine(@Auth final ArnoldPrincipal user) {
        return list(user);
    }

    @POST
    @Path("/mine/{groupId}/analyses")
    @Deprecated
    public ArnoldResponse<GroupAnalysisResponse> analyzeMine(
            @Auth final ArnoldPrincipal user, @PathParam("groupId") final String groupId,
            @Valid final GroupAnalysisResponse.StartRequest request) {
        return analyze(user, groupId, request);
    }
}
