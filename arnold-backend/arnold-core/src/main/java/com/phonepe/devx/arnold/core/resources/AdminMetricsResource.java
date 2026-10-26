package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.services.impl.ReviewFeedbackMetricsService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
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

    @GET
    @Path("/review-feedback")
    @RolesAllowed("superadmin")
    public ArnoldResponse<ReviewFeedbackMetricsResponse> reviewFeedback(@Auth final ServiceUserPrincipal user,
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
