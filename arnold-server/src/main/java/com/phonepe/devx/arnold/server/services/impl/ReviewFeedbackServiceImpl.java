package com.phonepe.devx.arnold.server.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.es.Position;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.arnold.server.elastic.ESStorageManager;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Note.NotePosition;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    private final CibotService cibotService;
    private final ESStorageManager esStorageManager;

    private static final String REVIEW_FEEDBACK_INDEX = "CORE_REVIEWS";

    @Override
    public int ingestFeedback(final ReviewFeedbackRequest request) {
        final var metadata = request.getMetadata();
        log.info("Received {} review feedback comments for MR {} in project {}",
                request.getComments().size(), metadata.getMergeRequestId(), metadata.getProjectId());

        final var project = fetchProject(metadata);
        final var mrAuthor = fetchMrAuthor(metadata);
        final long now = System.currentTimeMillis();

        final List<CompletableFuture<Boolean>> futures = request.getComments().stream()
                .map(comment -> CompletableFuture
                        .supplyAsync(() -> fetchNote(metadata, comment))
                        .thenApply(note -> buildDocument(comment, metadata, project, mrAuthor, note, now))
                        .thenApplyAsync(this::saveDocument))
                .toList();

        final int saved = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .mapToInt(success -> Boolean.TRUE.equals(success) ? 1 : 0)
                        .sum())
                .join();

        log.info("Persisted {}/{} review feedback documents to ES", saved, request.getComments().size());
        return saved;
    }

    // --- Cibot enrichment ---

    private Project fetchProject(final ReviewMetadata metadata) {
        final var project = cibotService.getProjectByGitlabId(metadata.getProjectId().intValue()).orElse(null);
        if (project == null) {
            log.warn("Could not fetch project info from cibot for projectId={}", metadata.getProjectId());
        }
        return project;
    }

    private User fetchMrAuthor(final ReviewMetadata metadata) {
        final var mrAuthor = cibotService.getUserByGitlabId(metadata.getMrAuthorId().intValue()).orElse(null);
        if (mrAuthor == null) {
            log.warn("Could not fetch MR author from cibot for mrAuthorId={}", metadata.getMrAuthorId());
        }
        return mrAuthor;
    }

    private Note fetchNote(final ReviewMetadata metadata, final ReviewComment comment) {
        return cibotService.getMergeRequestNote(
                metadata.getProjectId().intValue(),
                metadata.getMergeRequestId().intValue(),
                comment.getNoteId().intValue()
        ).orElse(null);
    }

    // --- Document building ---

    private ReviewFeedbackDocument buildDocument(final ReviewComment comment,
                                                  final ReviewMetadata metadata,
                                                  final Project project,
                                                  final User mrAuthor,
                                                  final Note note,
                                                  final long now) {
        final var optNote = Optional.ofNullable(note);

        return ReviewFeedbackDocument.builder()
                // MR metadata
                .projectId(metadata.getProjectId())
                .mergeRequestId(metadata.getMergeRequestId())
                .mrAuthorId(metadata.getMrAuthorId())
                // Cibot enriched fields
                .mrAuthor(mrAuthor)
                .projectName(project != null ? project.getName() : null)
                .projectNamespace(project != null ? project.getNamespace() : null)
                .teamId(project != null ? project.getOlympusTeamId() : null)
                .podName(project != null ? project.getPod() : null)
                // Note details
                .noteId(comment.getNoteId())
                .discussionId(comment.getDiscussionId())
                .noteCreatedAt(optNote.map(Note::getCreatedAt).orElse(null))
                .comment(optNote.map(Note::getBody).orElse(null))
                .position(optNote.map(Note::getPosition).map(this::toPosition).orElse(null))
                .reviewer(optNote.map(Note::getAuthor).orElse(null))
                // Review assessment
                .commentType(comment.getCommentType())
                .hasSuggestion(comment.getHasSuggestion())
                .acceptanceStatus(comment.getStatus())
                .score(comment.getScore() != null ? comment.getScore().doubleValue() : null)
                .reasoning(comment.getReasoning())
                .priority(comment.getPriority())
                .commentCategory(comment.getCommentCategory())
                .ingestedAt(now)
                .build();
    }

    private Position toPosition(final NotePosition notePosition) {
        return Position.builder()
                .newPath(notePosition.getNewPath())
                .oldPath(notePosition.getOldPath())
                .newLine(notePosition.getNewLine())
                .oldLine(notePosition.getOldLine())
                .build();
    }

    // --- Persistence ---

    private boolean saveDocument(final ReviewFeedbackDocument document) {
        try {
            esStorageManager.save(REVIEW_FEEDBACK_INDEX, document.documentId(), document);
            return true;
        } catch (Exception e) {
            log.error("Failed to save review feedback document id={}", document.documentId(), e);
            return false;
        }
    }
}
