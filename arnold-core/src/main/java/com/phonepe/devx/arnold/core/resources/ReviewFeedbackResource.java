package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.core.models.feedback.TargetScope;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.Priority;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static com.phonepe.devx.arnold.core.common.Constants.AUTHORIZATION_HEADER;
import static com.phonepe.devx.arnold.core.common.Constants.OLYMPUS;
import static com.phonepe.devx.arnold.core.common.Constants.OLYMPUS_TOKEN;

@Slf4j
@Path("/review-feedback")
@Tag(name = "Review Feedback APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityScheme(
        type = SecuritySchemeType.APIKEY,
        name = OLYMPUS,
        paramName = AUTHORIZATION_HEADER,
        description = OLYMPUS_TOKEN,
        in = SecuritySchemeIn.HEADER
)
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackResource {

    private final ReviewFeedbackService reviewFeedbackService;

    @GET
    @Operation(description = "Query review feedback with optional status and scope filters", security = @SecurityRequirement(name = OLYMPUS))
    @RolesAllowed("review_feedback_read")
    public ArnoldResponse<List<ReviewFeedbackDocument>> getFeedback(
            @QueryParam("status") final List<AcceptanceStatus> statuses,
            @QueryParam("scope") @DefaultValue("GROUP") final TargetScope scope,
            @QueryParam("targetPath") final String targetPath,
            @QueryParam("teamName") final String teamName,
            @QueryParam("daysBack") @DefaultValue("30") @Min(1) @Max(90) final int daysBack,
            @QueryParam("minScore") @DefaultValue("0.5") final double minScore,
            @QueryParam("minPriority") @DefaultValue("LOW") final Priority minPriority,
            @QueryParam("limit") @DefaultValue("500") @Min(1) @Max(1000) final int limit) {

        log.info("Querying review feedback: statuses={}, scope={}, targetPath={}, team={}, daysBack={}, minScore={}",
                statuses, scope, targetPath, teamName, daysBack, minScore);

        final var params = FeedbackQueryParams.builder()
                .statuses(statuses)
                .scope(scope)
                .targetPath(targetPath)
                .teamName(teamName)
                .daysBack(daysBack)
                .minScore(minScore)
                .minPriority(minPriority)
                .limit(limit)
                .build();

        return ArnoldResponse.ok(reviewFeedbackService.getFeedback(params));
    }

    @POST
    @Path("/v1/ingest")
    @Operation(description = "Ingest review feedback from the AI review workflow pipeline", security = @SecurityRequirement(name = OLYMPUS))
    @RolesAllowed("review_feedback_write")
    public ArnoldResponse<Integer> ingestFeedback(@Valid final ReviewFeedbackRequest request) {
        return ArnoldResponse.ok(reviewFeedbackService.ingestFeedback(request));
    }
}
