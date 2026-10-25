package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.DashboardService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.dashboard.DashboardResponse;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

@Path("/app/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class DashboardResource {
    private final DashboardService service;

    @GET
    @Path("/permissions")
    @RolesAllowed("superadmin")
    public ArnoldResponse<Map<String, Boolean>> permissions(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(Map.of("superadmin", true));
    }

    @GET
    @Path("/bootstrap")
    @PermitAll
    public ArnoldResponse<DashboardResponse> bootstrap(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.get(AuthenticatedUser.details(user)));
    }
}
