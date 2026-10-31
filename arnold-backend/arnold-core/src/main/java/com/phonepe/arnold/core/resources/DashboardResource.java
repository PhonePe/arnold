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
import com.phonepe.arnold.core.services.DashboardService;
import com.phonepe.arnold.core.services.SetupService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.dashboard.DashboardResponse;
import com.phonepe.arnold.models.dashboard.SetupResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
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
    private final SetupService setup;

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

    @GET
    @Path("/setup")
    @PermitAll
    public ArnoldResponse<SetupResponse> setup(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(setup.setup(AuthenticatedUser.id(user)));
    }
}
