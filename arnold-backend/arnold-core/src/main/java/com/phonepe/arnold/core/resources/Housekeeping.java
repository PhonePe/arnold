package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.core.services.HousekeepingService;
import com.phonepe.arnold.models.es.BackfillResult;
import com.phonepe.arnold.models.request.BackfillRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.phonepe.arnold.core.common.Constants.BEARER_AUTH;
import static com.phonepe.arnold.core.common.Constants.BEARER_TOKEN;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.annotation.security.RolesAllowed;

@Slf4j
@Path("/housekeeping")
@Tag(name = "Housekeeping APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityScheme(
        type = SecuritySchemeType.HTTP,
        name = BEARER_AUTH,
        scheme = "bearer",
        description = BEARER_TOKEN
)
@AllArgsConstructor(onConstructor_ = @Inject)
public class Housekeeping {
    private final HousekeepingService housekeepingService;

    @GET
    @Path("/v1/ping/{prefix}")
    @Operation(description = "Ping on service")
    public ArnoldResponse<String> sync(
            final @PathParam("prefix") String prefix,
            final @QueryParam("message") @DefaultValue("Hello world") String message) {
        return ArnoldResponse.ok(String.format("%s %s", prefix, message));
    }

    @PUT
    @Path("/v1/backfill/review-feedback")
    @Operation(description = "Idempotent Re-enrichment of review-feedback records that were persisted with missing enrichments "
            + "Run with dryRun=true first to validate the worklist and confirm downstream availability.", security = @SecurityRequirement(name = BEARER_AUTH))
    @RolesAllowed("backfill_review_feedback")
    public ArnoldResponse<BackfillResult> backfillReviewFeedback(@Valid final BackfillRequest request) {
        return ArnoldResponse.ok(housekeepingService.backfillReviewFeedback(request));
    }

    @PUT
    @Path("/v1/migrate/merge-request-groups")
    @Operation(description = "Idempotent migration of legacy per-user merge-request group collections into "
            + "shared per-group records. Skips groups that already exist.", security = @SecurityRequirement(name = BEARER_AUTH))
    @RolesAllowed("backfill_review_feedback")
    public ArnoldResponse<Integer> migrateMergeRequestGroups() {
        return ArnoldResponse.ok(housekeepingService.migrateMergeRequestGroups());
    }
}
