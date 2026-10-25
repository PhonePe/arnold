package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.model.sync.UserSyncControlState;
import com.phonepe.arnold.core.model.sync.UserSyncMode;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.UserSyncControlService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.admin.UpdateUserSyncModeRequest;
import com.phonepe.arnold.models.admin.UserSyncControlResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Locale;

@Path("/admin/v1/user-sync")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class AdminUserSyncResource {
    private final UserSyncControlService service;

    @GET
    @RolesAllowed("superadmin")
    public ArnoldResponse<UserSyncControlResponse> status(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(response(service.status()));
    }

    @PUT
    @RolesAllowed("superadmin")
    public ArnoldResponse<UserSyncControlResponse> update(@Auth final ArnoldPrincipal user,
                                                          final @Valid UpdateUserSyncModeRequest request) {
        try {
            final var mode = UserSyncMode.valueOf(request.getMode().trim().toUpperCase(Locale.ROOT));
            return ArnoldResponse.ok(response(service.setMode(mode)));
        } catch (IllegalArgumentException invalidMode) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Mode must be AUTO, POLLING, or CALLBACK_ONLY").build();
        }
    }

    @POST
    @Path("/callback-observation/reset")
    @RolesAllowed("superadmin")
    public ArnoldResponse<UserSyncControlResponse> resetCallbackObservation(
            @Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(response(service.resetCallbackObservation()));
    }

    private UserSyncControlResponse response(final UserSyncControlState state) {
        return new UserSyncControlResponse(state.configuredMode().name(), state.effectiveMode().name(),
                state.callbackObserved(), state.lastCallbackAt(), state.pollingEnabled(), state.leaderId(),
                state.leaderLeaseUntil(), state.currentNodeLeader(), state.currentNodeId());
    }
}
