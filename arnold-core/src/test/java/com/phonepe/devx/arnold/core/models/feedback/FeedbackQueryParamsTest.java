package com.phonepe.devx.arnold.core.models.feedback;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackQueryParamsTest {

    @Nested
    class IsProjectTargetPathValid {

        @Test
        void shouldReturnTrue_whenScopeIsNotProject() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.GROUP)
                    .targetPath("devx")
                    .build();

            assertTrue(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnTrue_whenScopeIsSubgroup() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.SUBGROUP)
                    .targetPath("devx")
                    .build();

            assertTrue(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndTargetPathNull() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath(null)
                    .build();

            assertFalse(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndTargetPathBlank() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("   ")
                    .build();

            assertFalse(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndNoSlash() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("myproject")
                    .build();

            assertFalse(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndSlashAtStart() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("/myproject")
                    .build();

            assertFalse(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnFalse_whenProjectScopeAndSlashAtEnd() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("devx/")
                    .build();

            assertFalse(params.isProjectTargetPathValid());
        }

        @Test
        void shouldReturnTrue_whenProjectScopeAndFullyQualifiedPath() {
            final var params = FeedbackQueryParams.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("devx/arnold")
                    .build();

            assertTrue(params.isProjectTargetPathValid());
        }
    }

    @Nested
    class BuilderDefaults {

        @Test
        void shouldApplyDefaults_whenBuiltWithDefaults() {
            final var params = FeedbackQueryParams.builder().build();

            assertTrue(params.isProjectTargetPathValid());
        }
    }
}
