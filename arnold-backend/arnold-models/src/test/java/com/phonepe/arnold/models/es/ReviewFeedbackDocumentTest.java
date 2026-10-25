package com.phonepe.arnold.models.es;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewFeedbackDocumentTest {

    @Test
    void documentId_shouldReturnProjectIdColonMrIdColonNoteId() {
        final var doc = ReviewFeedbackDocument.builder()
                .project(ProjectInfo.builder().projectId(10L).build())
                .mrId(20L)
                .noteId(55L)
                .build();

        assertEquals("10:20:55", doc.documentId());
    }

    @Test
    void documentId_shouldHandleLargeIds() {
        final var doc = ReviewFeedbackDocument.builder()
                .project(ProjectInfo.builder().projectId(999999L).build())
                .mrId(888888L)
                .noteId(777777L)
                .build();

        assertEquals("999999:888888:777777", doc.documentId());
    }

    @Test
    void documentId_shouldHandleNullProject() {
        final var doc = ReviewFeedbackDocument.builder()
                .project(null)
                .mrId(20L)
                .noteId(55L)
                .build();

        assertEquals("null:20:55", doc.documentId());
    }

    @Test
    void builder_shouldPreserveSuggestionProvenance() {
        final var doc = ReviewFeedbackDocument.builder()
                .sourceHarness("opencode")
                .sourceModel("openai/gpt-4.1")
                .build();

        assertEquals("opencode", doc.getSourceHarness());
        assertEquals("openai/gpt-4.1", doc.getSourceModel());
    }
}
