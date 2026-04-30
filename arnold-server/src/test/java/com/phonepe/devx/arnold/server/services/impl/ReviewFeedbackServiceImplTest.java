package com.phonepe.devx.arnold.server.services.impl;

import com.phonepe.devx.arnold.models.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.phonepe.devx.arnold.models.request.ReviewFeedbackTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewFeedbackServiceImplTest {

    private ReviewFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewFeedbackServiceImpl();
    }

    @Nested
    class IngestFeedback {

        @Test
        void testIngestFeedback_shouldReturnOne_whenSingleComment() {
            final var request = buildRequest(List.of(buildComment(1L)));

            final var result = service.ingestFeedback(request);

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldReturnCount_whenMultipleComments() {
            final var comments = List.of(
                    buildComment(1L),
                    buildComment(2L),
                    buildComment(3L),
                    buildComment(4L)
            );
            final var request = buildRequest(comments);

            final var result = service.ingestFeedback(request);

            assertEquals(4, result);
        }
    }
}
