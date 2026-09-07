package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationCompletionRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.services.PipelineService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;

import static com.phonepe.devx.arnold.core.common.Constants.AUTHORIZATION_HEADER;
import static com.phonepe.devx.arnold.core.common.Constants.OLYMPUS;
import static com.phonepe.devx.arnold.core.common.Constants.OLYMPUS_TOKEN;

@Slf4j
@Path("/api/v1/pipeline")
@Tag(name = "Pipeline APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityScheme(
        type = SecuritySchemeType.APIKEY,
        name = OLYMPUS,
        paramName = AUTHORIZATION_HEADER,
        description = OLYMPUS_TOKEN,
        in = SecuritySchemeIn.HEADER
)
public class PipelineResource {

    private final PipelineService pipelineService;

    @Inject
    public PipelineResource(final PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Trigger the rules-from-feedback pipeline in Clio.
     * Called from the Arnold UI "Generate Rules" button.
     */
    @POST
    @Path("/generate-rules")
    @Operation(summary = "Trigger rules-from-feedback pipeline", security = @SecurityRequirement(name = OLYMPUS))
    @RolesAllowed("pipeline_trigger")
    public ArnoldResponse<Map<String, Object>> triggerRuleGeneration(
            @NotNull @Valid TriggerRuleGenRequest request) {

        final var result = pipelineService.triggerRuleGeneration(request);

        return ArnoldResponse.ok(Map.of(
                "executionId", result.getExecutionId(),
                "pipelineId", result.getPipelineId(),
                "message", "Rule generation pipeline triggered"
        ));
    }

    @PUT
    @Path("/rule-generations/{executionId}")
    @Operation(summary = "Record a completed rule-generation pipeline",
            security = @SecurityRequirement(name = OLYMPUS))
    @RolesAllowed("pipeline_update")
    public ArnoldResponse<RuleGenerationExecution> completeRuleGeneration(
            @PathParam("executionId") String executionId,
            @NotNull @Valid RuleGenerationCompletionRequest request) {
        return ArnoldResponse.ok(pipelineService.completeRuleGeneration(executionId, request));
    }
}
