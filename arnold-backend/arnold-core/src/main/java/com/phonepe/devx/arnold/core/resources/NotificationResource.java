package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.NotificationService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.notification.NotificationPageResponse;
import com.phonepe.devx.arnold.models.notification.NotificationResponse;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/notifications/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class NotificationResource {
    private final NotificationService service;

    @GET
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<NotificationPageResponse> list(
            @Auth final ServiceUserPrincipal user,
            @QueryParam("limit") final Integer limit,
            @QueryParam("before") final String before) {
        return ArnoldResponse.ok(service.list(AuthenticatedUser.id(user), limit == null ? 20 : limit, before));
    }

    @PATCH
    @Path("/mine/{notificationId}/read")
    @PermitAll
    public ArnoldResponse<NotificationResponse> markRead(
            @Auth final ServiceUserPrincipal user,
            @PathParam("notificationId") final String notificationId) {
        return ArnoldResponse.ok(service.markRead(AuthenticatedUser.id(user), notificationId));
    }

    @POST
    @Path("/mine/read-all")
    @PermitAll
    public ArnoldResponse<NotificationPageResponse> markAllRead(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.markAllRead(AuthenticatedUser.id(user)));
    }
}
