package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenRequest;
import com.phonepe.devx.arnold.core.models.feedback.TriggerRuleGenerationResult;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationCompletionRequest;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationStatus;
import com.phonepe.devx.arnold.core.services.PipelineService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.request.TargetScope;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DropwizardExtensionsSupport.class)
class PipelineResourceTest {

    private static final PipelineService pipelineService = mock(PipelineService.class);

    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new PipelineResource(pipelineService))
            .build();

    @AfterEach
    void tearDown() {
        reset(pipelineService);
    }

    @Test
    void shouldReturnOk_whenTriggerRuleGenerationSucceeds() {
        when(pipelineService.triggerRuleGeneration(any())).thenReturn(
                TriggerRuleGenerationResult.builder().executionId("exec-42").pipelineId(42L).build());

        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(request));

        assertEquals(200, response.getStatus());
        final ArnoldResponse<java.util.Map<String, Object>> body =
                response.readEntity(new GenericType<>() {});
        assertTrue(body.isSuccess());
        assertEquals(42, body.getResponse().get("pipelineId"));
        assertEquals("exec-42", body.getResponse().get("executionId"));
        assertEquals("Rule generation pipeline triggered", body.getResponse().get("message"));
        verify(pipelineService).triggerRuleGeneration(any(TriggerRuleGenRequest.class));
    }

    @Test
    void shouldReturn422_whenTargetPathMissing() {
        final var request = TriggerRuleGenRequest.builder()
                .targetPath(null)
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(request));

        assertEquals(422, response.getStatus());
    }

    @Test
    void shouldReturn422_whenRequestIsNull() {
        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity("null", MediaType.APPLICATION_JSON_TYPE));

        assertEquals(422, response.getStatus());
    }

    @Test
    void shouldReturn422_whenMinScoreIsOutOfRange() {
        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .minScore(2)
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(request));

        assertEquals(422, response.getStatus());
    }

    @Test
    void shouldReturn422_whenProjectPathIsNotFullyQualified() {
        final var request = TriggerRuleGenRequest.builder()
                .targetPath("checkout-api")
                .scope(TargetScope.PROJECT)
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(request));

        assertEquals(422, response.getStatus());
    }

    @Test
    void shouldReturn500_whenServiceThrows() {
        when(pipelineService.triggerRuleGeneration(any()))
                .thenThrow(new RuntimeException("GitLab unavailable"));

        final var request = TriggerRuleGenRequest.builder()
                .targetPath("devx/checkout-api")
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/generate-rules")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.json(request));

        assertEquals(500, response.getStatus());
    }

    @Test
    void shouldReturnOk_whenCompleteRuleGenerationSucceeds() {
        final var execution = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();
        when(pipelineService.completeRuleGeneration(any(), any())).thenReturn(execution);

        final var request = RuleGenerationCompletionRequest.builder()
                .status(RuleGenerationStatus.SUCCEEDED)
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/rule-generations/exec-1")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .put(Entity.json(request));

        assertEquals(200, response.getStatus());
        final ArnoldResponse<RuleGenerationExecution> body =
                response.readEntity(new GenericType<>() {});
        assertTrue(body.isSuccess());
        assertEquals("exec-1", body.getResponse().getExecutionId());
        assertEquals(RuleGenerationStatus.SUCCEEDED, body.getResponse().getStatus());
        verify(pipelineService).completeRuleGeneration(eq("exec-1"), any(RuleGenerationCompletionRequest.class));
    }

    @Test
    void shouldReturn422_whenCompletionStatusIsNull() {
        final var request = RuleGenerationCompletionRequest.builder()
                .status(null)
                .build();

        final Response response = RESOURCES.target("/api/v1/pipeline/rule-generations/exec-1")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .put(Entity.json(request));

        assertEquals(422, response.getStatus());
    }
}
