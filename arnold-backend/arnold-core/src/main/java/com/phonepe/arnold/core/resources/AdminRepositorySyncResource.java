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

import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import com.phonepe.arnold.core.services.RepositorySyncService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.sync.RepositorySyncOverviewResponse;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/admin/v1/repository-sync")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class AdminRepositorySyncResource {
    private final RepositorySyncService service;

    @GET
    @RolesAllowed("superadmin")
    public ArnoldResponse<RepositorySyncOverviewResponse> status(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.status());
    }

    /**
     * Starts a run in the background; the response reflects the state right after scheduling it.
     */
    @POST
    @RolesAllowed("superadmin")
    public ArnoldResponse<RepositorySyncOverviewResponse> trigger(@Auth final ArnoldPrincipal user) {
        final var started = service.trigger();
        final var status = service.status();
        return ArnoldResponse.ok(new RepositorySyncOverviewResponse(status.enabled(), started || status.running(),
                status.providers()));
    }
}
