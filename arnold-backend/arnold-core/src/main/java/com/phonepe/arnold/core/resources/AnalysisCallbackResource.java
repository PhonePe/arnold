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

import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.services.AnalysisService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.analysis.AnalysisResponse;
import com.phonepe.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.arnold.models.analysis.ReviewPersonaDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/analysis-callbacks/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class AnalysisCallbackResource {
    private final AnalysisService analysisService;

    @POST
    @Path("/persona-completed")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<AnalysisResponse> completePersona(@Valid final PersonaCompletionRequest request) {
        return ArnoldResponse.ok(analysisService.completePersona(request));
    }

    @GET
    @Path("/runs/{runId}/personas")
    @RolesAllowed("analysis_callback_write")
    public ArnoldResponse<List<ReviewPersonaDefinition>> reviewPersonas(@PathParam("runId") final String runId,
                                                                        @QueryParam("refs") final String refs) {
        return ArnoldResponse.ok(analysisService.reviewPersonas(runId, refs));
    }
}
