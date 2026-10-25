package com.phonepe.arnold.core.resources;
import javax.annotation.security.RolesAllowed;
import com.phonepe.arnold.models.integration.CopilotExecutionTokenResponse;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.integration.CopilotAttemptRequest;
import com.phonepe.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.arnold.models.integration.CopilotDeviceResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
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
    private final CopilotExecutionService executions;

    @POST
    @Path("/execution-token")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<CopilotExecutionTokenResponse> executionToken(
            @HeaderParam("X-Arnold-Copilot-Key") final String key) {
        return ArnoldResponse.ok(executions.exchange(key));
    }

    @GET
    @Path("/status")
    public ArnoldResponse<CopilotConnectionResponse> status(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.status(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/device/initiate")
    public ArnoldResponse<CopilotDeviceResponse> initiate(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.initiate(AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/device/poll")
    public ArnoldResponse<CopilotDeviceResponse> poll(@Auth final ArnoldPrincipal user,
                                                      @NotNull @Valid final CopilotAttemptRequest request) {
        return ArnoldResponse.ok(service.poll(AuthenticatedUser.id(user), request.attemptId()));
    }

    @DELETE
    @Path("/device/attempt")
    public ArnoldResponse<CopilotConnectionResponse> cancel(@Auth final ArnoldPrincipal user,
                                                             @NotNull @Valid final CopilotAttemptRequest request) {
        return ArnoldResponse.ok(service.cancel(AuthenticatedUser.id(user), request.attemptId()));
    }

    @POST
    @Path("/verify")
    public ArnoldResponse<CopilotConnectionResponse> verify(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.verify(AuthenticatedUser.id(user)));
    }

    @DELETE
    @Path("/connection")
    public ArnoldResponse<CopilotConnectionResponse> disconnect(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.disconnect(AuthenticatedUser.id(user)));
    }
}
