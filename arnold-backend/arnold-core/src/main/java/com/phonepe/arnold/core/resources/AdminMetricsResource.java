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

import com.phonepe.arnold.core.services.impl.ReviewFeedbackMetricsService;
import com.phonepe.arnold.core.services.impl.TokenUsageMetricsService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.arnold.models.admin.TokenUsageMetricsResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;

@Path("/admin/v1/metrics")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class AdminMetricsResource {
    private final ReviewFeedbackMetricsService service;
    private final TokenUsageMetricsService tokenUsage;

    @GET
    @Path("/token-usage")
    @RolesAllowed("superadmin")
    public ArnoldResponse<TokenUsageMetricsResponse> tokenUsage(@Auth final ArnoldPrincipal user,
                                                                @QueryParam("from") final Long from,
                                                                @QueryParam("to") final Long to) {
        return ArnoldResponse.ok(tokenUsage.metrics(from, to));
    }

    @GET
    @Path("/review-feedback")
    @RolesAllowed("superadmin")
    public ArnoldResponse<ReviewFeedbackMetricsResponse> reviewFeedback(@Auth final ArnoldPrincipal user,
                                                                        @QueryParam("from") final Long from,
                                                                        @QueryParam("to") final Long to,
                                                                        @QueryParam("project") final String project,
                                                                        @QueryParam("model") final String model,
                                                                        @QueryParam("personaId") final String personaId,
                                                                        @QueryParam("status") final String status,
                                                                        @QueryParam("priority") final String priority,
                                                                        @QueryParam("commentType") final String commentType,
                                                                        @QueryParam("category") final String category,
                                                                        @QueryParam("hasSuggestion") final Boolean hasSuggestion) {
        final var filters = new HashMap<String, Object>();
        filters.put("project", project);
        filters.put("model", model);
        filters.put("personaId", personaId);
        filters.put("status", status);
        filters.put("priority", priority);
        filters.put("commentType", commentType);
        filters.put("category", category);
        filters.put("hasSuggestion", hasSuggestion);
        return ArnoldResponse.ok(service.metrics(from, to, filters));
    }
}
