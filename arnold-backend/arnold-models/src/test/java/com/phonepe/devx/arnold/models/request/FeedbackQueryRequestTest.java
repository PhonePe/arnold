package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackQueryRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    class IsProjectTargetPathValid {

        @Test
        void shouldReturnTrue_whenScopeIsNotProject() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.GROUP)
                    .targetPath("devx")
                    .build();

            assertTrue(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnTrue_whenScopeIsSubgroup() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.SUBGROUP)
                    .targetPath("devx")
                    .build();

            assertTrue(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndTargetPathNull() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath(null)
                    .build();

            assertFalse(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndTargetPathBlank() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("   ")
                    .build();

            assertFalse(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndNoSlash() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("myproject")
                    .build();

            assertFalse(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndSlashAtStart() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("/myproject")
                    .build();

            assertFalse(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndSlashAtEnd() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("devx/")
                    .build();

            assertFalse(request.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnTrue_whenProjectScopeAndFullyQualifiedPath() {
            final var request = FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("devx/arnold")
                    .build();

            assertTrue(request.isProjectTargetPathValid());
        }
    }

    @Nested
    class Serialization {

        @Test
        void shouldApplyDefaults_whenOptionalFieldsAreOmitted() throws Exception {
            final var request = MAPPER.readValue("{}", FeedbackQueryRequest.class);

            assertEquals(TargetScope.GROUP, request.getScope());
            assertEquals(30, request.getDaysBack());
            assertEquals(0.5D, request.getMinScore());
            assertEquals(500, request.getLimit());
            assertEquals(0, request.getOffset());
        }

        @Test
        void shouldDeserializeAllFilters() throws Exception {
            final var request = MAPPER.readValue("""
                    {
                      "statuses": ["applied", "partially_applied"],
                      "scope": "PROJECT",
                      "targetPath": "devx/arnold",
                      "teamName": "team-1",
                      "daysBack": 7,
                      "minScore": 0.7,
                      "minPriority": "low",
                      "limit": 25,
                      "offset": 50
                    }
                    """, FeedbackQueryRequest.class);

            assertEquals(List.of(AcceptanceStatus.APPLIED, AcceptanceStatus.PARTIALLY_APPLIED),
                    request.getStatuses());
            assertEquals(TargetScope.PROJECT, request.getScope());
            assertEquals("devx/arnold", request.getTargetPath());
            assertEquals("team-1", request.getTeamName());
            assertEquals(7, request.getDaysBack());
            assertEquals(0.7D, request.getMinScore());
            assertEquals(Priority.LOW, request.getMinPriority());
            assertEquals(25, request.getLimit());
            assertEquals(50, request.getOffset());
        }

        @Test
        void shouldUseExplicitScopeWireValue() throws Exception {
            assertEquals("\"project\"", MAPPER.writeValueAsString(TargetScope.PROJECT));
            assertEquals(TargetScope.SUBGROUP, TargetScope.fromString("SUBGROUP"));
            assertThrows(IllegalArgumentException.class, () -> TargetScope.fromString(null));
            assertThrows(IllegalArgumentException.class, () -> TargetScope.fromString("repository"));
        }
    }
}
