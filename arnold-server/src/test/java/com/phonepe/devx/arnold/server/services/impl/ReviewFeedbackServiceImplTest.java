package com.phonepe.devx.arnold.server.services.impl;

import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.models.request.*;
import com.phonepe.devx.arnold.server.elastic.ESStorageManager;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.phonepe.devx.arnold.models.request.ReviewFeedbackTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
                        .mergeRequestId(20L)
                        .mrAuthorId(30L)
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
    }
}
