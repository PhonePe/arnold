package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
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
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
    public ArnoldResponse<List<ReviewFeedbackDocument>> queryFeedback(
            @Valid @BeanParam final FeedbackQueryParams params) {

        log.info("Querying review feedback: statuses={}, scope={}, targetPath={}, team={}, daysBack={}, minScore={}",
                params.getStatuses(), params.getScope(), params.getTargetPath(), params.getTeamName(),
                params.getDaysBack(), params.getMinScore());

        return ArnoldResponse.ok(reviewFeedbackService.queryFeedback(params));
    }

    @POST
    @Path("/v1/ingest")
    @Operation(description = "Ingest review feedback from the AI review workflow pipeline", security = @SecurityRequirement(name = OLYMPUS))
    @RolesAllowed("review_feedback_write")
    public ArnoldResponse<Integer> ingestFeedback(@Valid final ReviewFeedbackRequest request) {
        return ArnoldResponse.ok(reviewFeedbackService.ingestFeedback(request));
    }
}
