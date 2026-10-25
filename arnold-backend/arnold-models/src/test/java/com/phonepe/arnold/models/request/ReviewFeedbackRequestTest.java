/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.models.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.phonepe.arnold.models.request.ReviewFeedbackTestData.buildComment;
import static com.phonepe.arnold.models.request.ReviewFeedbackTestData.buildMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFeedbackRequestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    class BuilderTests {

        @Test
        void testBuild_shouldCreateValidRequest_whenAllFieldsProvided() {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(buildMetadata())
                    .comments(List.of(buildComment()))
                    .build();

            assertNotNull(request.getMetadata());
            assertEquals(1, request.getComments().size());
            assertEquals(100L, request.getMetadata().getProjectId());
            assertEquals(200L, request.getMetadata().getMrId());
        }

        @Test
        void testBuild_shouldSupportMultipleComments_whenListProvided() {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(buildMetadata())
                    .comments(List.of(buildComment(), buildComment()))
                    .build();

            assertEquals(2, request.getComments().size());
        }
    }

    @Nested
    class SerializationTests {

        @Test
        void testSerialization_shouldRoundTrip_whenValidRequest() throws Exception {
            final var request = ReviewFeedbackRequest.builder()
                    .metadata(buildMetadata())
                    .comments(List.of(buildComment()))
                    .build();

            final var json = MAPPER.writeValueAsString(request);
            final var deserialized = MAPPER.readValue(json, ReviewFeedbackRequest.class);

            assertEquals(request.getMetadata().getProjectId(), deserialized.getMetadata().getProjectId());
            assertEquals(request.getMetadata().getMrId(), deserialized.getMetadata().getMrId());
            assertEquals(request.getComments().size(), deserialized.getComments().size());
        }

        @Test
        void testDeserialization_shouldIgnoreUnknownFields_whenExtraFieldsPresent() throws Exception {
            final var json = """
                    {
                      "metadata": {
                        "project_id": 100,
                        "merge_request_id": 200,
                        "mr_author_id": 300,
                        "unknown_field": "ignored"
                      },
                      "comments": [{
                        "note_id": 1,
                        "discussion_id": "disc-1",
                        "comment_type": "inline",
                        "has_suggestion": true,
                        "status": "applied",
                        "score": 0.85,
                        "reasoning": "Good catch",
                        "priority": "critical",
                        "comment_category": "bug",
                        "source_harness": "codex",
                        "source_model": "gpt-5.6-sol",
                        "extra_field": "also_ignored"
                      }],
                      "top_level_unknown": true
                    }
                    """;

            final var request = MAPPER.readValue(json, ReviewFeedbackRequest.class);

            assertEquals(100L, request.getMetadata().getProjectId());
            assertEquals(1, request.getComments().size());
            assertEquals(AcceptanceStatus.APPLIED, request.getComments().get(0).getStatus());
            assertEquals("codex", request.getComments().get(0).getSourceHarness());
            assertEquals("gpt-5.6-sol", request.getComments().get(0).getSourceModel());
        }
    }

    @Nested
    class ReviewCommentTests {

        @Test
        void testReviewComment_shouldPreserveAllFields_whenBuilt() {
            final var comment = buildComment();

            assertEquals(1L, comment.getNoteId());
            assertEquals("disc-1", comment.getDiscussionId());
            assertEquals(CommentType.INLINE, comment.getCommentType());
            assertTrue(comment.getHasSuggestion());
            assertEquals(AcceptanceStatus.APPLIED, comment.getStatus());
            assertEquals(new BigDecimal("0.85"), comment.getScore());
            assertEquals("Good catch", comment.getReasoning());
            assertEquals(Priority.HIGH, comment.getPriority());
            assertEquals("bug", comment.getCommentCategory());
            assertEquals("codex", comment.getSourceHarness());
            assertEquals("gpt-5.6-sol", comment.getSourceModel());
        }

        @Test
        void testReviewComment_shouldSerializeJsonProperties_whenSerialized() throws Exception {
            final var comment = buildComment();
            final var json = MAPPER.writeValueAsString(comment);

            assertTrue(json.contains("\"note_id\""));
            assertTrue(json.contains("\"discussion_id\""));
            assertTrue(json.contains("\"comment_type\""));
            assertTrue(json.contains("\"has_suggestion\""));
            assertTrue(json.contains("\"comment_category\""));
            assertTrue(json.contains("\"source_harness\""));
            assertTrue(json.contains("\"source_model\""));
        }
    }

    @Nested
    class ReviewMetadataTests {

        @Test
        void testReviewMetadata_shouldPreserveAllFields_whenBuilt() {
            final var metadata = buildMetadata();

            assertEquals(100L, metadata.getProjectId());
            assertEquals(200L, metadata.getMrId());
            assertEquals(300L, metadata.getMrAuthorId());
        }

        @Test
        void testReviewMetadata_shouldSerializeJsonProperties_whenSerialized() throws Exception {
            final var metadata = buildMetadata();
            final var json = MAPPER.writeValueAsString(metadata);

            assertTrue(json.contains("\"project_id\""));
            assertTrue(json.contains("\"mr_id\""));
            assertTrue(json.contains("\"mr_author_id\""));
        }
    }

    @Nested
    class EnumTests {

        @Test
        void testAcceptanceStatus_shouldDeserialize_whenJsonPropertyUsed() throws Exception {
            assertEquals(AcceptanceStatus.APPLIED, MAPPER.readValue("\"applied\"", AcceptanceStatus.class));
            assertEquals(AcceptanceStatus.PARTIALLY_APPLIED, MAPPER.readValue("\"partially_applied\"", AcceptanceStatus.class));
            assertEquals(AcceptanceStatus.IGNORED, MAPPER.readValue("\"ignored\"", AcceptanceStatus.class));
            assertEquals(AcceptanceStatus.NOT_APPLICABLE, MAPPER.readValue("\"not_applicable\"", AcceptanceStatus.class));
        }

        @Test
        void testCommentType_shouldDeserialize_whenJsonPropertyUsed() throws Exception {
            assertEquals(CommentType.INLINE, MAPPER.readValue("\"inline\"", CommentType.class));
            assertEquals(CommentType.GENERAL, MAPPER.readValue("\"general\"", CommentType.class));
        }

        @Test
        void testCommentType_shouldUseExplicitWireValue() {
            assertEquals("inline", CommentType.INLINE.getValue());
            assertEquals(CommentType.GENERAL, CommentType.fromString("GENERAL"));
            assertThrows(IllegalArgumentException.class, () -> CommentType.fromString(null));
            assertThrows(IllegalArgumentException.class, () -> CommentType.fromString("file"));
        }

        @Test
        void testPriority_shouldDeserialize_whenJsonPropertyUsed() throws Exception {
            assertEquals(Priority.CRITICAL, MAPPER.readValue("\"critical\"", Priority.class));
            assertEquals(Priority.HIGH, MAPPER.readValue("\"high\"", Priority.class));
            assertEquals(Priority.MEDIUM, MAPPER.readValue("\"medium\"", Priority.class));
            assertEquals(Priority.LOW, MAPPER.readValue("\"low\"", Priority.class));
        }

        @Test
        void testAcceptanceStatus_shouldUseExplicitWireValue() {
            assertEquals("partially_applied", AcceptanceStatus.PARTIALLY_APPLIED.getValue());
            assertEquals("not_applicable", AcceptanceStatus.NOT_APPLICABLE.getValue());
            assertEquals(AcceptanceStatus.PARTIALLY_APPLIED,
                    AcceptanceStatus.fromString("partially_applied"));
            assertThrows(IllegalArgumentException.class, () -> AcceptanceStatus.fromString(null));
        }

        @Test
        void testPriority_shouldUseExplicitWireValue() {
            assertEquals("critical", Priority.CRITICAL.getValue());
            assertEquals(Priority.LOW, Priority.fromString("low"));
            assertThrows(IllegalArgumentException.class, () -> Priority.fromString(null));
        }

        @Test
        void testPriority_fromStringShouldMatchByNameIgnoreCase() {
            assertEquals(Priority.CRITICAL, Priority.fromString("CRITICAL"));
            assertEquals(Priority.HIGH, Priority.fromString("HIGH"));
            assertEquals(Priority.MEDIUM, Priority.fromString("Medium"));
        }

        @Test
        void testPriority_fromStringShouldThrowForUnknownValue() {
            assertThrows(IllegalArgumentException.class, () -> Priority.fromString("urgent"));
        }

        @Test
        void testAcceptanceStatus_fromStringShouldMatchByNameIgnoreCase() {
            assertEquals(AcceptanceStatus.APPLIED, AcceptanceStatus.fromString("APPLIED"));
            assertEquals(AcceptanceStatus.IGNORED, AcceptanceStatus.fromString("IGNORED"));
        }

        @Test
        void testAcceptanceStatus_fromStringShouldThrowForUnknownValue() {
            assertThrows(IllegalArgumentException.class, () -> AcceptanceStatus.fromString("rejected"));
        }
    }
}
