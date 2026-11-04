package com.phonepe.devx.arnold.models.es;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupAnalysisRunDocumentTest {

    @Test
    void testGroupAnalysisRunDocument_shouldExposeComponents_whenBuilt() {
        final var document = new GroupAnalysisRunDocument("run-1", GroupAnalysisRunDocument.DOCUMENT_TYPE, "group-1",
                "Payments", "completed", "alice", 2, List.of("security"), 3, 1, 1, 77L, 1_000, 4_000, 3_000, 120L);

        assertEquals("group_analysis_run", document.type());
        assertEquals(List.of("security"), document.reviewPersonaIds());
        assertEquals(120L, document.totalTokens());
    }
}
