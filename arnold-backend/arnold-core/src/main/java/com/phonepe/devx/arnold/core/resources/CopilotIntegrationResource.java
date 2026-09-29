package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.integration.CopilotAttemptRequest;
import com.phonepe.devx.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.devx.arnold.models.integration.CopilotDeviceResponse;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/copilot-integrations/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@AllArgsConstructor(onConstructor_ = @Inject)
public class CopilotIntegrationResource {
    private final CopilotConnectionService service;

    @GET
    @Path("/status")
    public ArnoldResponse<CopilotConnectionResponse> status(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.status(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/device/initiate")
    public ArnoldResponse<CopilotDeviceResponse> initiate(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.initiate(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/device/poll")
    public ArnoldResponse<CopilotDeviceResponse> poll(@Auth final ServiceUserPrincipal user,
                                                      @NotNull @Valid final CopilotAttemptRequest request) {
        return ArnoldResponse.ok(service.poll(AuthenticatedUser.id(user), request.attemptId()));
    }

    @DELETE
    @Path("/device/attempt")
    public ArnoldResponse<CopilotConnectionResponse> cancel(@Auth final ServiceUserPrincipal user,
                                                             @NotNull @Valid final CopilotAttemptRequest request) {
        return ArnoldResponse.ok(service.cancel(AuthenticatedUser.id(user), request.attemptId()));
    }

    @POST
    @Path("/verify")
    public ArnoldResponse<CopilotConnectionResponse> verify(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.verify(AuthenticatedUser.id(user)));
    }

    @DELETE
    @Path("/connection")
    public ArnoldResponse<CopilotConnectionResponse> disconnect(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.disconnect(AuthenticatedUser.id(user)));
    }
}
