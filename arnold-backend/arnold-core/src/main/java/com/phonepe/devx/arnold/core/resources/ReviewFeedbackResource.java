package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.FeedbackQueryRequest;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static com.phonepe.devx.arnold.core.common.Constants.BEARER_AUTH;
import static com.phonepe.devx.arnold.core.common.Constants.BEARER_TOKEN;

@Slf4j
@Path("/review-feedback")
@Tag(name = "Review Feedback APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackResource {

    private final ReviewFeedbackService reviewFeedbackService;

    @POST
    @Operation(description = "Query review feedback with optional status and scope filters", security = @SecurityRequirement(name = BEARER_AUTH))
    @RolesAllowed("review_feedback_read")
    public ArnoldResponse<List<ReviewFeedbackDocument>> queryFeedback(
            @Valid final FeedbackQueryRequest request) {

        log.info("Querying review feedback: statuses={}, scope={}, targetPath={}, team={}, daysBack={}, minScore={}",
                request.getStatuses(), request.getScope(), request.getTargetPath(), request.getTeamName(),
                request.getDaysBack(), request.getMinScore());

        return ArnoldResponse.ok(reviewFeedbackService.queryFeedback(request));
    }

    @POST
    @Path("/v1/ingest")
    @Operation(description = "Ingest review feedback from the AI review workflow pipeline")
    @RolesAllowed("review_feedback_write")
    public ArnoldResponse<Integer> ingestFeedback(@Valid final ReviewFeedbackRequest request) {
        return ArnoldResponse.ok(reviewFeedbackService.ingestFeedback(request));
    }
}
