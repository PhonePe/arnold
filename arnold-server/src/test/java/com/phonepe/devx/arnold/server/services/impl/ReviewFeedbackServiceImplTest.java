package com.phonepe.devx.arnold.server.services.impl;

import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.CommentType;
import com.phonepe.devx.arnold.models.request.Priority;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.arnold.server.elastic.ESStorageManager;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private CibotService cibotService;

    @Mock
    private ESStorageManager esStorageManager;

    private ReviewFeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewFeedbackServiceImpl(cibotService, esStorageManager);
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
                .build();
    }

    private static ReviewFeedbackRequest buildRequest(List<ReviewComment> comments) {
        return ReviewFeedbackRequest.builder()
                .metadata(ReviewMetadata.builder()
                        .projectId(10L)
                        .mrId(20L)
                        .mrIid(30L)
                        .mrAuthorId(40L)
                        .build())
                .comments(comments)
                .build();
    }

    private static Project buildProject() {
        return Project.builder()
                .id(10)
                .name("test-project")
                .namespace("devx")
                .pod("core-pod")
                .olympusTeamId("team-42")
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
                        .email("user@example.com")
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
            when(cibotService.getProjectByGitlabId(anyInt()))
                    .thenReturn(Optional.of(buildProject()));
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
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
        void testIngestFeedback_shouldSucceed_whenCibotUnavailable() {
            when(cibotService.getProjectByGitlabId(anyInt()))
                    .thenReturn(Optional.empty());
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(1L)));
            final var result = service.ingestFeedback(request);

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldSucceed_whenNoteInfoMissing() {
            when(cibotService.getProjectByGitlabId(anyInt()))
                    .thenReturn(Optional.of(buildProject()));
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(1L)));
            final var result = service.ingestFeedback(request);

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldPassMrIidToCibot() {
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty());

            final var request = buildRequest(List.of(buildComment(99L)));
            service.ingestFeedback(request);

            verify(cibotService).getMergeRequestNote(
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
        void testIngestFeedback_shouldFetchMrAuthorFromCibot() {
            final var request = buildRequest(List.of(buildComment(1L)));

            service.ingestFeedback(request);

            verify(cibotService).getUserByGitlabId(40); // mrAuthorId
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
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(noteWithoutPosition));

            final var result = service.ingestFeedback(buildRequest(List.of(buildComment(1L))));

            assertEquals(1, result);
        }

        @Test
        void testIngestFeedback_shouldReturnZero_whenBuildDocumentThrows() {
            // Mocking the interface directly bypasses CibotServiceHttpImpl's try-catch,
            // so the exception propagates through fetchNote into supplyAsync and is
            // caught by .exceptionally(ex -> false)
            when(cibotService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("unexpected cibot failure"));

            final var result = service.ingestFeedback(buildRequest(List.of(buildComment(1L))));

            assertEquals(0, result);
        }
    }
}

