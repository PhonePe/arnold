/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.services.NotificationService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.notification.NotificationPageResponse;
import com.phonepe.arnold.models.notification.NotificationResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
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
            @Auth final ArnoldPrincipal user,
            @QueryParam("limit") final Integer limit,
            @QueryParam("before") final String before) {
        return ArnoldResponse.ok(service.list(AuthenticatedUser.id(user), limit == null ? 20 : limit, before));
    }

    @PATCH
    @Path("/mine/{notificationId}/read")
    @PermitAll
    public ArnoldResponse<NotificationResponse> markRead(
            @Auth final ArnoldPrincipal user,
            @PathParam("notificationId") final String notificationId) {
        return ArnoldResponse.ok(service.markRead(AuthenticatedUser.id(user), notificationId));
    }

    @POST
    @Path("/mine/read-all")
    @PermitAll
    public ArnoldResponse<NotificationPageResponse> markAllRead(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.markAllRead(AuthenticatedUser.id(user)));
    }
}
