package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.services.ScmMetadataService;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.AcceptanceStatus;
import com.phonepe.arnold.models.request.CommentType;
import com.phonepe.arnold.models.request.FeedbackQueryRequest;
import com.phonepe.arnold.models.request.Priority;
import com.phonepe.arnold.models.request.ReviewComment;
import com.phonepe.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.arnold.models.request.ReviewMetadata;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.models.request.TargetScope;
import com.phonepe.arnold.models.scm.Note;
import com.phonepe.arnold.models.scm.Project;
import com.phonepe.arnold.models.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewFeedbackServiceImplTest {

    @Mock
    private ScmMetadataService scmMetadataService;

    @Mock
    private ESStorageManager esStorageManager;

    private ReviewFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewFeedbackServiceImpl(scmMetadataService, esStorageManager, Executors.newCachedThreadPool());
    }

    private static ReviewComment buildComment(Long noteId) {
        return ReviewComment.builder()
                .noteId(noteId)
                .discussionId("disc-" + noteId)
                .commentType(CommentType.INLINE)
                .hasSuggestion(false)
                .status(AcceptanceStatus.APPLIED)
                .score(new BigDecimal("0.9"))
                .reasoning("Valid finding")
                .priority(Priority.MEDIUM)
                .commentCategory("style")
                .sourceHarness("opencode")
                .sourceModel("openai/gpt-4.1")
                .build();
    }

    private static ReviewFeedbackRequest buildRequest(List<ReviewComment> comments) {
        return ReviewFeedbackRequest.builder()
                .metadata(ReviewMetadata.builder()
                        .projectId(10L)
                        .mrId(20L)
                        .mrIid(30L)
                        .mrAuthorId(40L)
                        .assessedSha("a".repeat(40))
                        .build())
                .comments(comments)
                .build();
    }

    private static Project buildProject() {
        return Project.builder()
                .id(10)
                .name("test-project")
                .namespace("acme")
                .pod("core-pod")
                .teamId("team-42")
                .build();
    }

    private static Note buildNoteDetail(int noteId) {
        return Note.builder()
                .id(noteId)
                .createdAt("2026-04-27T10:00:00Z")
                .body("Review comment for note " + noteId)
                .author(User.builder()
                        .id(100)
                        .name("Reviewer")
                        .username("reviewer")
                        .email("reviewer@example.com")
                        .build())
                .position(Note.NotePosition.builder()
                        .newPath("src/main/java/Example.java")
                        .newLine(42)
                        .build())
                .build();
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
            when(scmMetadataService.getProject(anyInt()))
                    .thenReturn(Optional.of(buildProject()));
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenAnswer(inv -> Optional.of(buildNoteDetail(inv.getArgument(2))));

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

        @Test
        void testIngestFeedback_shouldSucceed_whenScmMetadataUnavailable() {
            when(scmMetadataService.getProject(anyInt()))
                    .thenReturn(Optional.empty());
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(1L)));
            final var result = service.ingestFeedback(request);

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldSucceed_whenNoteInfoMissing() {
            when(scmMetadataService.getProject(anyInt()))
                    .thenReturn(Optional.of(buildProject()));
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(1L)));
            final var result = service.ingestFeedback(request);

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldPassMrIidToScmMetadata() {
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(99L)));
            service.ingestFeedback(request);

            verify(scmMetadataService).getMergeRequestNote(
                    eq(10),   // projectId
                    eq(30),   // mrIid
                    eq(99)    // noteId
            );
        }

        @Test
        void testIngestFeedback_shouldReturnZero_whenAllSavesFail() {
            doThrow(new RuntimeException("ES down"))
                    .when(esStorageManager).save(any(), any(), any());

            final var comments = List.of(buildComment(1L), buildComment(2L));
            final var result = service.ingestFeedback(buildRequest(comments));

            assertEquals(0, result);
        }

        @Test
        void testIngestFeedback_shouldCountPartialSuccess_whenSomeSavesFail() {
            doNothing()
                    .doThrow(new RuntimeException("ES flaky"))
                    .when(esStorageManager).save(any(), any(), any());

            final var comments = List.of(buildComment(1L), buildComment(2L));
            final var result = service.ingestFeedback(buildRequest(comments));

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldSaveDocumentWithCorrectId() {
            final var request = buildRequest(List.of(buildComment(55L)));

            service.ingestFeedback(request);

            final var idCaptor = ArgumentCaptor.forClass(String.class);
            verify(esStorageManager).save(eq("CORE_REVIEWS"), idCaptor.capture(), any());
            // document id format: projectId:mrId:noteId
            assertEquals("10:20:55", idCaptor.getValue());
        }

        @Test
        void testIngestFeedback_shouldPersistSuggestionProvenance() {
            service.ingestFeedback(buildRequest(List.of(buildComment(55L))));

            final var documentCaptor = ArgumentCaptor.forClass(ReviewFeedbackDocument.class);
            verify(esStorageManager).save(
                    eq("CORE_REVIEWS"),
                    eq("10:20:55"),
                    documentCaptor.capture()
            );

            assertEquals("opencode", documentCaptor.getValue().getSourceHarness());
            assertEquals("openai/gpt-4.1", documentCaptor.getValue().getSourceModel());
            assertEquals("a".repeat(40), documentCaptor.getValue().getAssessedSha());
        }

        @Test
        void testIngestFeedback_shouldFetchMrAuthorFromScmMetadata() {
            final var request = buildRequest(List.of(buildComment(1L)));

            service.ingestFeedback(request);

            verify(scmMetadataService).getUser(40); // mrAuthorId
        }

        @Test
        void testIngestFeedback_shouldSaveWithNullScore_whenCommentScoreIsNull() {
            final var comment = ReviewComment.builder()
                    .noteId(1L)
                    .discussionId("disc-1")
                    .commentType(CommentType.INLINE)
                    .hasSuggestion(false)
                    .status(AcceptanceStatus.APPLIED)
                    .score(null)           // ← null score branch
                    .reasoning("no score")
                    .priority(Priority.LOW)
                    .commentCategory("style")
                    .build();

            final var result = service.ingestFeedback(buildRequest(List.of(comment)));

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldHandleNoteWithNullPosition() {
            final var noteWithoutPosition = Note.builder()
                    .id(1)
                    .createdAt("2026-04-27T10:00:00Z")
                    .body("some comment")
                    .author(User.builder().id(100).name("Reviewer").build())
                    .position(null)        // ← null position branch
                    .build();
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(noteWithoutPosition));

            final var result = service.ingestFeedback(buildRequest(List.of(buildComment(1L))));

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldReturnZero_whenBuildDocumentThrows() {
            // Mocking the interface directly bypasses GitLabScmMetadataService's try-catch,
            // so the exception propagates through fetchNote into supplyAsync and is
            // caught by .exceptionally(ex -> false)
            when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("unexpected scm failure"));

            final var result = service.ingestFeedback(buildRequest(List.of(buildComment(1L))));

            assertEquals(0, result);
        }
    }
    @Nested
    class QueryFeedback {

        @Test
        void testFeedbackByNote_shouldReturnDocumentsByNoteId() {
            final var document = ReviewFeedbackDocument.builder()
                    .noteId(7L)
                    .acceptanceStatus(AcceptanceStatus.APPLIED)
                    .assessedSha("a".repeat(40))
                    .build();
            when(esStorageManager.searchEntities(eq("CORE_REVIEWS"), any(), any(), eq(List.of()), eq(2), eq(0)))
                    .thenReturn(List.of(document));

            final Map<Long, ReviewFeedbackDocument> result = service.feedbackByNote(10L, 30L, List.of(7L, 8L));

            assertEquals(document, result.get(7L));
        }

        @Test
        void testQueryFeedback_shouldUseRequestedLimitAndOffset() {
            final var document = ReviewFeedbackDocument.builder().noteId(1L).build();
            when(esStorageManager.searchEntities(eq("CORE_REVIEWS"), any(), any(), any(), eq(25), eq(50)))
                    .thenReturn(List.of(document));

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .statuses(List.of(AcceptanceStatus.IGNORED))
                    .scope(TargetScope.GROUP)
                    .targetPath("acme")
                    .daysBack(7)
                    .minScore(0.0)
                    .minPriority(Priority.LOW)
                    .limit(25)
                    .offset(50)
                    .build());

            assertEquals(List.of(document), result);
            verify(esStorageManager).searchEntities(eq("CORE_REVIEWS"), any(), any(), any(), eq(25), eq(50));
        }

        @Test
        void testQueryFeedback_shouldWork_whenStatusesNull() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .statuses(null)
                    .targetPath("acme")
                    .build());

            assertTrue(result.isEmpty());
        }

        @Test
        void testQueryFeedback_shouldWork_whenStatusesEmpty() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .statuses(List.of())
                    .targetPath("acme")
                    .build());

            assertTrue(result.isEmpty());
        }

        @Test
        void testQueryFeedback_shouldWork_whenMinScoreNull() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .minScore(null)
                    .targetPath("acme")
                    .build());

            assertTrue(result.isEmpty());
        }

        @Test
        void testQueryFeedback_shouldWork_whenTeamNameBlank() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .teamName("   ")
                    .targetPath("acme")
                    .build());

            assertTrue(result.isEmpty());
        }

        @Test
        void testQueryFeedback_shouldWork_whenTeamNameProvided() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .teamName("team-42")
                    .targetPath("acme")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenMinPriorityNull() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .minPriority(null)
                    .targetPath("acme")
                    .build());

            assertTrue(result.isEmpty());
        }

        @Test
        void testQueryFeedback_shouldWork_whenMinPriorityProvided() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .minPriority(Priority.CRITICAL)
                    .targetPath("acme")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenScopeIsSubgroup() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(TargetScope.SUBGROUP)
                    .targetPath("acme/payments")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenScopeIsProject() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("acme/arnold")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenScopeIsNull() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(null)
                    .targetPath("acme")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenTargetPathHasTrailingSlash() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(TargetScope.GROUP)
                    .targetPath("acme/")
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldWork_whenTargetPathNull() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            service.queryFeedback(FeedbackQueryRequest.builder()
                    .targetPath(null)
                    .build());

            verify(esStorageManager).searchEntities(any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        void testQueryFeedback_shouldThrow_whenProjectScopePathHasNoSlash() {
            assertThrows(BadRequestException.class, () -> service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("myproject")
                    .build()));
        }

        @Test
        void testQueryFeedback_shouldThrow_whenProjectScopePathEndsWithSlash() {
            assertThrows(BadRequestException.class, () -> service.queryFeedback(FeedbackQueryRequest.builder()
                    .scope(TargetScope.PROJECT)
                    .targetPath("acme/")
                    .build()));
        }

        @Test
        void testQueryFeedback_shouldReturnEmpty_whenSearchReturnsEmpty() {
            when(esStorageManager.searchEntities(any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            final var result = service.queryFeedback(FeedbackQueryRequest.builder()
                    .statuses(List.of(AcceptanceStatus.IGNORED, AcceptanceStatus.APPLIED))
                    .scope(TargetScope.GROUP)
                    .targetPath("acme")
                    .teamName("team-1")
                    .daysBack(15)
                    .minScore(0.8)
                    .minPriority(Priority.HIGH)
                    .limit(10)
                    .offset(5)
                    .build());

            assertTrue(result.isEmpty());
        }
    }
}
