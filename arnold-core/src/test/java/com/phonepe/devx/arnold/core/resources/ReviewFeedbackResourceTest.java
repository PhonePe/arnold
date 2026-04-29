package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static com.phonepe.devx.arnold.models.request.ReviewFeedbackTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class ReviewFeedbackResourceTest {

    private static final ReviewFeedbackService reviewFeedbackService = mock(ReviewFeedbackService.class);

    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new ReviewFeedbackResource(reviewFeedbackService))
            .build();

    @AfterEach
    void tearDown() {
        reset(reviewFeedbackService);
    }

    @Nested
    class IngestFeedback {

        @Test
        void shouldReturnOk_whenSingleComment() {
            final var request = buildRequest(List.of(buildComment()));
            when(reviewFeedbackService.ingestFeedback(any())).thenReturn(1);

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(200, response.getStatus());
            final ArnoldResponse<Integer> body = response.readEntity(new GenericType<>() {});
            assertTrue(body.isSuccess());
            assertEquals(1, body.getResponse());
            verify(reviewFeedbackService).ingestFeedback(any(ReviewFeedbackRequest.class));
        }

        @Test
        void shouldReturnCommentCount_whenMultipleComments() {
            final var comments = List.of(buildComment(), buildComment(), buildComment());
            final var request = buildRequest(comments);
            when(reviewFeedbackService.ingestFeedback(any())).thenReturn(3);

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(200, response.getStatus());
            final ArnoldResponse<Integer> body = response.readEntity(new GenericType<>() {});
            assertTrue(body.isSuccess());
            assertEquals(3, body.getResponse());
            verify(reviewFeedbackService, times(1)).ingestFeedback(any(ReviewFeedbackRequest.class));
        }

        @Test
        void shouldDelegateToService() {
            final var request = buildRequest(List.of(buildComment()));
            when(reviewFeedbackService.ingestFeedback(any())).thenReturn(1);

            RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            verify(reviewFeedbackService).ingestFeedback(any(ReviewFeedbackRequest.class));
            verifyNoMoreInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturnServerError_whenServiceThrows() {
            final var request = buildRequest(List.of(buildComment()));
            when(reviewFeedbackService.ingestFeedback(any()))
                    .thenThrow(new RuntimeException("ES unavailable"));

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(500, response.getStatus());
        }

        @Test
        void shouldReturnCorrectContentType() {
            final var request = buildRequest(List.of(buildComment()));
            when(reviewFeedbackService.ingestFeedback(any())).thenReturn(1);

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
