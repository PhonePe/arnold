package com.phonepe.devx.arnold.models.es;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewFeedbackDocumentTest {

    @Test
    void documentId_shouldReturnProjectIdColonMergeRequestIdColonNoteId() {
        final var doc = ReviewFeedbackDocument.builder()
                .projectId(10L)
                .mergeRequestId(20L)
                .noteId(55L)
                .build();

        assertEquals("10:20:55", doc.documentId());
    }

    @Test
    void documentId_shouldHandleLargeIds() {
        final var doc = ReviewFeedbackDocument.builder()
                .projectId(999999L)
                .mergeRequestId(888888L)
                .noteId(777777L)
                .build();

        assertEquals("999999:888888:777777", doc.documentId());
    }
}
