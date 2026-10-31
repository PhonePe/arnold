package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.AnalysisService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.devx.arnold.models.analysis.ReviewPersonaDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/analysis-callbacks/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisCallbackResource {
    private final AnalysisService analysisService;

    @POST
    @Path("/persona-completed")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<AnalysisResponse> completePersona(@Valid final PersonaCompletionRequest request) {
        return ArnoldResponse.ok(analysisService.completePersona(request));
    }

    @GET
    @Path("/runs/{runId}/personas")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<List<ReviewPersonaDefinition>> reviewPersonas(@PathParam("runId") final String runId,
                                                                        @QueryParam("refs") final String refs) {
        return ArnoldResponse.ok(analysisService.reviewPersonas(runId, refs));
    }
}
