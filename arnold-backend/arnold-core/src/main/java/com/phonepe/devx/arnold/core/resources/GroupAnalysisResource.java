package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.GroupAnalysisService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/group-analyses/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class GroupAnalysisResource {
    private final GroupAnalysisService service;

    @GET
    @PermitAll
    public ArnoldResponse<List<GroupAnalysisResponse>> list(@QueryParam("groupId") final String groupId,
                                                             @QueryParam("before") final Long before,
                                                             @QueryParam("limit") @DefaultValue("50") final int limit) {
        return ArnoldResponse.ok(service.list(groupId, before, limit));
    }

    @GET
    @Path("/{runId}")
    @PermitAll
    public ArnoldResponse<GroupAnalysisResponse> get(@PathParam("runId") final String runId) {
        return ArnoldResponse.ok(service.get(runId));
    }

    @POST
    @Path("/{runId}/retry")
    @PermitAll
    public ArnoldResponse<GroupAnalysisResponse> retry(@Auth final ArnoldPrincipal user, @PathParam("runId") final String runId) {
        return ArnoldResponse.ok(service.retry(AuthenticatedUser.id(user), runId));
    }

    @POST
    @Path("/{runId}/retry-publication")
    @PermitAll
    public ArnoldResponse<GroupAnalysisResponse> retryPublication(@Auth final ArnoldPrincipal user,
                                                                 @PathParam("runId") final String runId) {
        return retry(user, runId);
    }
}
