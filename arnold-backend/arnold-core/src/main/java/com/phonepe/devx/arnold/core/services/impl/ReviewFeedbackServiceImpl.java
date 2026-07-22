package com.phonepe.devx.arnold.core.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.es.Position;
import com.phonepe.devx.arnold.models.es.ProjectInfo;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument.ReviewFeedbackDocumentBuilder;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Note.NotePosition;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    private final CibotService cibotService;
    private final ESStorageManager esStorageManager;
    @Named(BindingNames.ASYNC_TASK_EXECUTOR)
    private final ExecutorService asyncTaskExecutor;

    @Override
    public int ingestFeedback(final ReviewFeedbackRequest request) {
        final var metadata = request.getMetadata();
        log.info("Received {} review comments feedback for MR {} in project {}",
                request.getComments().size(), metadata.getMrIid(), metadata.getProjectId());

        final var project = fetchProject(metadata);
        final var mrAuthor = fetchMrAuthor(metadata);
        final long nowMs = System.currentTimeMillis();

        final List<CompletableFuture<Boolean>> futures = request.getComments().stream()
                .map(comment -> CompletableFuture
                        .supplyAsync(() -> fetchNote(metadata, comment), asyncTaskExecutor)
                        .thenApply(note -> buildDocument(comment, metadata, project, mrAuthor, note, nowMs))
                        .thenApplyAsync(this::saveDocument, asyncTaskExecutor)
                        .exceptionally(ex -> {
                            log.error("Unexpected failure processing comment noteId={}", comment.getNoteId(), ex);
                            return false;
                        }))
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

    private Optional<Project> fetchProject(final ReviewMetadata metadata) {
        final var project = cibotService.getProjectByGitlabId(metadata.getProjectId().intValue());
        if (project.isEmpty()) {
            log.warn("Could not fetch project info from cibot for projectId={}", metadata.getProjectId());
        }
        return project;
    }

    private Optional<User> fetchMrAuthor(final ReviewMetadata metadata) {
        final var mrAuthor = cibotService.getUserByGitlabId(metadata.getMrAuthorId().intValue());
        if (mrAuthor.isEmpty()) {
            log.warn("Could not fetch MR author from cibot for mrAuthorId={}", metadata.getMrAuthorId());
        }
        return mrAuthor;
    }

    private Optional<Note> fetchNote(final ReviewMetadata metadata, final ReviewComment comment) {
        final var note = cibotService.getMergeRequestNote(
                metadata.getProjectId().intValue(),
                metadata.getMrIid().intValue(),
                comment.getNoteId().intValue()
        );
        if (note.isEmpty()) {
            log.warn("Could not fetch note from cibot for noteId={}", comment.getNoteId());
        }
        return note;
    }

    // --- Document building ---

    private ReviewFeedbackDocument buildDocument(final ReviewComment comment,
                                                 final ReviewMetadata metadata,
                                                 final Optional<Project> project,
                                                 final Optional<User> mrAuthor,
                                                 final Optional<Note> note,
                                                 final long nowMs) {
        return applyMetadata(ReviewFeedbackDocument.builder(), metadata)
                .then(b -> applyProjectEnrichment(b, metadata, project, mrAuthor))
                .then(b -> applyNoteDetails(b, comment, note))
                .then(b -> applyCommentAssessment(b, comment))
                .ingestedAt(nowMs)
                .build();
    }

    private BuilderStep applyMetadata(final ReviewFeedbackDocumentBuilder b, final ReviewMetadata metadata) {
        b.mrId(metadata.getMrId())
                .mrIid(metadata.getMrIid())
                .mrAuthorId(metadata.getMrAuthorId());
        return new BuilderStep(b);
    }

    private void applyProjectEnrichment(final ReviewFeedbackDocumentBuilder b,
                                        final ReviewMetadata metadata,
                                        final Optional<Project> project,
                                        final Optional<User> mrAuthor) {
        b.mrAuthor(mrAuthor.orElse(null))
                .project(ProjectInfo.builder()
                        .projectId(metadata.getProjectId())
                        .projectName(project.map(Project::getName).orElse(null))
                        .projectNamespace(project.map(Project::getNamespace).orElse(null))
                        .teamId(project.map(Project::getOlympusTeamId).orElse(null))
                        .podName(project.map(Project::getPod).orElse(null))
                        .build());
    }

    private void applyNoteDetails(final ReviewFeedbackDocumentBuilder b,
                                  final ReviewComment comment,
                                  final Optional<Note> optNote) {
        b.noteId(comment.getNoteId())
                .discussionId(comment.getDiscussionId())
                .noteCreatedAt(optNote.map(Note::getCreatedAt).orElse(null))
                .comment(optNote.map(Note::getBody).orElse(null))
                .position(optNote.map(Note::getPosition).map(this::toPosition).orElse(null))
                .reviewer(optNote.map(Note::getAuthor).orElse(null));
    }

    private void applyCommentAssessment(final ReviewFeedbackDocumentBuilder b, final ReviewComment comment) {
        b.commentType(comment.getCommentType())
                .hasSuggestion(comment.getHasSuggestion())
                .acceptanceStatus(comment.getStatus())
                .score(comment.getScore() != null ? comment.getScore().doubleValue() : null)
                .reasoning(comment.getReasoning())
                .priority(comment.getPriority())
                .commentCategory(comment.getCommentCategory());
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
            esStorageManager.save("CORE_REVIEWS", document.documentId(), document);
            return true;
        } catch (Exception e) {
            log.error("Failed to save review feedback document id={}", document.documentId(), e);
            return false;
        }
    }

    // --- Builder step helper ---

    @RequiredArgsConstructor
    private static final class BuilderStep {

        private final ReviewFeedbackDocumentBuilder b;

        BuilderStep then(final Consumer<ReviewFeedbackDocumentBuilder> step) {
            step.accept(b);
            return this;
        }

        ReviewFeedbackDocumentBuilder ingestedAt(final long ts) {
            return b.ingestedAt(ts);
        }
    }
}

