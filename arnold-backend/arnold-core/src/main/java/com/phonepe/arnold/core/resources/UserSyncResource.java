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
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.UserSyncService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.sync.UserSyncStatusResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
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
    public ArnoldResponse<UserSyncStatusResponse> status(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.status(VersionControlProviderType.GITLAB, AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<UserSyncStatusResponse> retry(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.retry(VersionControlProviderType.GITLAB, AuthenticatedUser.id(user)));
    }
}
