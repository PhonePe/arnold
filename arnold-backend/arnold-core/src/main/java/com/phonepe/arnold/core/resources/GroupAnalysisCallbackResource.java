package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.services.GroupAnalysisService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/group-analysis-callbacks/v1/{runId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class GroupAnalysisCallbackResource {
    private final GroupAnalysisService service;

    @GET
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<GroupAnalysisResponse.Manifest> manifest(@PathParam("runId") final String runId,
            @NotBlank @QueryParam("executionId") final String executionId) {
        return ArnoldResponse.ok(service.manifest(runId, executionId));
    }

    @GET
    @Path("/fresh")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<Boolean> fresh(@PathParam("runId") final String runId,
            @NotBlank @QueryParam("executionId") final String executionId) {
        return ArnoldResponse.ok(service.fresh(runId, executionId));
    }

    @PUT
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<GroupAnalysisResponse> update(@PathParam("runId") final String runId,
            @Valid final GroupAnalysisResponse.Update request) {
        return ArnoldResponse.ok(service.update(runId, request));
    }
}
