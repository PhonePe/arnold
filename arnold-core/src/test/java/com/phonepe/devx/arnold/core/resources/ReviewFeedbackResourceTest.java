package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.core.models.feedback.TargetScope;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.CommentType;
import com.phonepe.devx.arnold.models.request.Priority;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;

import static com.phonepe.devx.arnold.models.request.ReviewFeedbackTestData.buildComment;
import static com.phonepe.devx.arnold.models.request.ReviewFeedbackTestData.buildRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
                            .mrId(200L)
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
                            .mrId(200L)
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
            final var invalidComment = ReviewComment.builder()
                    .noteId(1L)
                    .discussionId("disc-1")
                    .commentType(CommentType.INLINE)
                    .hasSuggestion(false)
                    .status(AcceptanceStatus.APPLIED)
                    .score(new BigDecimal("1.5"))   // > 1.0
                    .reasoning("test")
                    .priority(Priority.HIGH)
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
            final var invalidComment = ReviewComment.builder()
                    .noteId(1L)
                    .discussionId("disc-1")
                    .commentType(CommentType.INLINE)
                    .hasSuggestion(false)
                    .status(AcceptanceStatus.APPLIED)
                    .score(new BigDecimal("-0.1"))  // < 0.0
                    .reasoning("test")
                    .priority(Priority.HIGH)
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

    @Nested
    class GetFeedback {

        @Test
        void shouldReturnOk_whenServiceReturnsItems() {
            when(reviewFeedbackService.queryFeedback(any()))
                    .thenReturn(List.of(ReviewFeedbackDocument.builder().noteId(1L).build()));

            final Response response = RESOURCES.target("/review-feedback")
                    .queryParam("targetPath", "devx")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertEquals(200, response.getStatus());
            final ArnoldResponse<List<ReviewFeedbackDocument>> body = response.readEntity(new GenericType<>() {});
            assertTrue(body.isSuccess());
            assertEquals(1, body.getResponse().size());
        }

        @Test
        void shouldDelegateQueryParams_whenFiltersAndPaginationProvided() {
            when(reviewFeedbackService.queryFeedback(any())).thenReturn(List.of());

            final Response response = RESOURCES.target("/review-feedback")
                    .queryParam("status", "applied")
                    .queryParam("status", "partially_applied")
                    .queryParam("scope", "PROJECT")
                    .queryParam("targetPath", "devx/arnold")
                    .queryParam("teamName", "team-1")
                    .queryParam("daysBack", "7")
                    .queryParam("minScore", "0.7")
                    .queryParam("minPriority", "low")
                    .queryParam("limit", "25")
                    .queryParam("offset", "50")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertEquals(200, response.getStatus());
            final var paramsCaptor = ArgumentCaptor.forClass(FeedbackQueryParams.class);
            verify(reviewFeedbackService).queryFeedback(paramsCaptor.capture());
            final var params = paramsCaptor.getValue();
            assertEquals(List.of(AcceptanceStatus.APPLIED, AcceptanceStatus.PARTIALLY_APPLIED),
                    params.getStatuses());
            assertEquals(TargetScope.PROJECT, params.getScope());
            assertEquals("devx/arnold", params.getTargetPath());
            assertEquals("team-1", params.getTeamName());
            assertEquals(7, params.getDaysBack());
            assertEquals(0.7D, params.getMinScore());
            assertEquals(Priority.LOW, params.getMinPriority());
            assertEquals(25, params.getLimit());
            assertEquals(50, params.getOffset());
        }

        @Test
        void shouldApplyDefaults_whenQueryParamsOmitted() {
            when(reviewFeedbackService.queryFeedback(any())).thenReturn(List.of());

            final Response response = RESOURCES.target("/review-feedback")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertEquals(200, response.getStatus());
            final var paramsCaptor = ArgumentCaptor.forClass(FeedbackQueryParams.class);
            verify(reviewFeedbackService).queryFeedback(paramsCaptor.capture());
            final var params = paramsCaptor.getValue();
            assertEquals(TargetScope.GROUP, params.getScope());
            assertEquals(30, params.getDaysBack());
            assertEquals(0.5D, params.getMinScore());
            assertEquals(500, params.getLimit());
            assertEquals(0, params.getOffset());
        }

        @Test
        void shouldReturn400_whenProjectPathIsNotFullyQualified() {
            final Response response = RESOURCES.target("/review-feedback")
                    .queryParam("scope", "PROJECT")
                    .queryParam("targetPath", "myproject")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .get();

            assertEquals(400, response.getStatus());
            verifyNoInteractions(reviewFeedbackService);
        }
    }

}
