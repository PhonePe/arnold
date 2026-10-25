package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.StatsService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.stats.StatsResponse;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/stats/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class StatsResource {
    private final StatsService service;

    @GET
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<StatsResponse> mine(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.personal(AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/organization")
    @PermitAll
    public ArnoldResponse<StatsResponse> organization() {
        return ArnoldResponse.ok(service.organization());
    }
}
