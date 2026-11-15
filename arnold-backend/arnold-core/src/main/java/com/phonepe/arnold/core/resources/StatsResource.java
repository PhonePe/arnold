/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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
import com.phonepe.arnold.core.services.StatsService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.stats.StatsResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
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
