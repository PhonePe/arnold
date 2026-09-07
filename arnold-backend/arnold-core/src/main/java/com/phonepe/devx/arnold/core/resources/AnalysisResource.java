package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.AnalysisService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.StartAnalysisRequest;
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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/analyses/v1")
@Tag(name = "Analysis APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisResource {
    private final AnalysisService analysisService;

    @POST
    @PermitAll
    @Operation(description = "Start an analysis")
    public ArnoldResponse<AnalysisResponse> start(@Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
                                                  @Valid final StartAnalysisRequest request) {
        return ArnoldResponse.ok(analysisService.start(request, AuthenticatedUser.id(user)));
    }

    @GET
    @PermitAll
    @Operation(description = "List analysis runs for the current user")
    public ArnoldResponse<List<AnalysisResponse>> list(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(analysisService.list(AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{runId}")
    @PermitAll
    @Operation(description = "Get an analysis run")
    public ArnoldResponse<AnalysisResponse> get(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @PathParam("runId") final String runId) {
        return ArnoldResponse.ok(analysisService.get(runId, AuthenticatedUser.id(user)));
    }
}
