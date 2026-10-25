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
import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.services.AnalysisService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.analysis.AnalysisResponse;
import com.phonepe.arnold.models.analysis.StartAnalysisRequest;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/analyses/v1")
@Tag(name = "Analysis APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisResource {
    private final AnalysisService analysisService;

    @POST
    @PermitAll
    @Operation(description = "Start an analysis")
    public ArnoldResponse<AnalysisResponse> start(@Parameter(hidden = true) @Auth final ArnoldPrincipal user,
                                                  @Valid final StartAnalysisRequest request) {
        return ArnoldResponse.ok(analysisService.start(request, AuthenticatedUser.id(user)));
    }

    @GET
    @PermitAll
    @Operation(description = "List analysis runs, newest first; optionally scoped to a merge request")
    public ArnoldResponse<List<AnalysisResponse>> list(@QueryParam("mergeRequestId") final String mergeRequestId,
                                                        @QueryParam("before") final Long before,
                                                        @QueryParam("limit") @DefaultValue("50") final int limit) {
        return ArnoldResponse.ok(analysisService.list(mergeRequestId, before, limit));
    }

    @GET
    @Path("/{runId}")
    @PermitAll
    @Operation(description = "Get an analysis run")
    public ArnoldResponse<AnalysisResponse> get(@PathParam("runId") final String runId) {
        return ArnoldResponse.ok(analysisService.get(runId));
    }
}
