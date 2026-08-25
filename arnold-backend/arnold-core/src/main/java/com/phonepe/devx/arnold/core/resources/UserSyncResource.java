package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;
import com.phonepe.olympus.im.client.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/gitlab-syncs/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class UserSyncResource {
    private final UserSyncService service;

    @GET
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<UserSyncStatusResponse> status(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.status(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<UserSyncStatusResponse> retry(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.retry(AuthenticatedUser.id(user)));
    }
}
