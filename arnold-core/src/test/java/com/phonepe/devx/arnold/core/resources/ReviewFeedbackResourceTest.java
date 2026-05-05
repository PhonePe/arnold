package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
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
import java.math.BigDecimal;
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

        @Test
        void shouldReturn200WithZero_whenServiceReturnsZero() {
            final var request = buildRequest(List.of(buildComment()));
            when(reviewFeedbackService.ingestFeedback(any())).thenReturn(0);

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(200, response.getStatus());
            final ArnoldResponse<Integer> body = response.readEntity(new GenericType<>() {});
            assertEquals(0, body.getResponse());
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void shouldReturn422_whenMrIidMissing() {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(ReviewMetadata.builder()
                            .projectId(100L)
                            .mergeRequestId(200L)
                            .mrAuthorId(300L)
                            // mrIid intentionally omitted
                            .build())
                    .comments(List.of(buildComment()))
                    .build();

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenProjectIdMissing() {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(ReviewMetadata.builder()
                            .mergeRequestId(200L)
                            .mrIid(25L)
                            .mrAuthorId(300L)
                            // projectId intentionally omitted
                            .build())
                    .comments(List.of(buildComment()))
                    .build();

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenCommentsListIsEmpty() {
            final var request = buildRequest(List.of());

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenCommentNoteIdIsNull() {
            final var invalidComment = buildComment(null);
            final var request = buildRequest(List.of(invalidComment));

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenScoreExceedsMax() {
            final var invalidComment = com.phonepe.devx.arnold.models.request.ReviewComment.builder()
                    .noteId(1L)
                    .discussionId("disc-1")
                    .commentType(com.phonepe.devx.arnold.models.request.CommentType.INLINE)
                    .hasSuggestion(false)
                    .status(com.phonepe.devx.arnold.models.request.AcceptanceStatus.APPLIED)
                    .score(new BigDecimal("1.5"))   // > 1.0
                    .reasoning("test")
                    .priority(com.phonepe.devx.arnold.models.request.Priority.HIGH)
                    .commentCategory("bug")
                    .build();
            final var request = buildRequest(List.of(invalidComment));

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenScoreBelowMin() {
            final var invalidComment = com.phonepe.devx.arnold.models.request.ReviewComment.builder()
                    .noteId(1L)
                    .discussionId("disc-1")
                    .commentType(com.phonepe.devx.arnold.models.request.CommentType.INLINE)
                    .hasSuggestion(false)
                    .status(com.phonepe.devx.arnold.models.request.AcceptanceStatus.APPLIED)
                    .score(new BigDecimal("-0.1"))  // < 0.0
                    .reasoning("test")
                    .priority(com.phonepe.devx.arnold.models.request.Priority.HIGH)
                    .commentCategory("bug")
                    .build();
            final var request = buildRequest(List.of(invalidComment));

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }

        @Test
        void shouldReturn422_whenMetadataIsNull() {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(null)
                    .comments(List.of(buildComment()))
                    .build();

            final Response response = RESOURCES.target("/review-feedback/v1/ingest")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(request));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }
    }
}

