package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.services.AnalysisExecutionService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.analysis.HarnessOption;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/analysis-execution/v1")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisExecutionResource {
    private final AnalysisExecutionService service;

    @GET
    public ArnoldResponse<List<HarnessOption>> options(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.options(AuthenticatedUser.id(user)));
    }
}
