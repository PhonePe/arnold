package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.commons.columbus.search.sort.FieldSort;
import com.phonepe.commons.columbus.search.sort.Sort;
import com.phonepe.commons.columbus.search.sort.SortOrder;
import com.phonepe.devx.arnold.core.common.ArnoldCommentMarker;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument.ReviewFeedbackDocumentBuilder;
import com.phonepe.devx.arnold.models.request.FeedbackQueryRequest;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.devx.arnold.core.common.Constants.INDEX_REFERENCE;
import static com.phonepe.devx.arnold.core.common.Constants.SORT_FIELD;


@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    private static final TypeReference<ReviewFeedbackDocument> REVIEW_FEEDBACK_TYPE_REF =
            new TypeReference<>() {};
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
                .then(b -> applyPersonaMarker(b, comment, metadata, note))
                .ingestedAt(nowMs)
                .origin(origin(metadata, note))
                .build();
    }

    /**
     * Attributes feedback to the persona tagged on the GitLab note when the workflow did not send {@code persona_id}.
     */
    private void applyPersonaMarker(final ReviewFeedbackDocumentBuilder b, final ReviewComment comment,
                                    final ReviewMetadata metadata, final Optional<Note> note) {
        if (comment.getPersonaId() != null && !comment.getPersonaId().isBlank()) {
            return;
        }
        note.map(Note::getBody).flatMap(ArnoldCommentMarker::persona).ifPresent(persona -> {
            b.personaId(persona.personaId()).personaVersion(persona.version());
            if (metadata.getAnalysisRunId() == null) {
                b.analysisRunId(persona.runId());
            }
        });
    }

    private String origin(final ReviewMetadata metadata, final Optional<Note> note) {
        final var postedByArnold = note.map(Note::getBody).map(ArnoldCommentMarker::isTagged).orElse(false);
        return metadata.getAnalysisRunId() != null || postedByArnold
                ? ReviewFeedbackDocument.ORIGIN_ARNOLD : ReviewFeedbackDocument.ORIGIN_WORKFLOW;
    }

    private BuilderStep applyMetadata(final ReviewFeedbackDocumentBuilder b, final ReviewMetadata metadata) {
        b.mrId(metadata.getMrId())
                .mrIid(metadata.getMrIid())
                .mrAuthorId(metadata.getMrAuthorId())
                .assessedSha(metadata.getAssessedSha())
                .analysisRunId(metadata.getAnalysisRunId());
        return new BuilderStep(b);
    }

    private void applyProjectEnrichment(final ReviewFeedbackDocumentBuilder b,
                                        final ReviewMetadata metadata,
                                        final Optional<Project> project,
                                        final Optional<User> mrAuthor) {
        ReviewFeedbackDocumentMapper.applyProjectAndAuthor(b, metadata.getProjectId(), project, mrAuthor);
    }

    private void applyNoteDetails(final ReviewFeedbackDocumentBuilder b,
                                  final ReviewComment comment,
                                  final Optional<Note> optNote) {
        ReviewFeedbackDocumentMapper.applyNoteDetails(b, comment.getNoteId(), comment.getDiscussionId(), optNote);
    }

    private void applyCommentAssessment(final ReviewFeedbackDocumentBuilder b, final ReviewComment comment) {
        b.commentType(comment.getCommentType())
                .hasSuggestion(comment.getHasSuggestion())
                .acceptanceStatus(comment.getStatus())
                .score(comment.getScore() != null ? comment.getScore().doubleValue() : null)
                .reasoning(comment.getReasoning())
                .priority(comment.getPriority())
                .commentCategory(comment.getCommentCategory())
                .sourceHarness(comment.getSourceHarness())
                .sourceModel(comment.getSourceModel())
                .personaId(comment.getPersonaId())
                .personaVersion(comment.getPersonaVersion());
    }

    // --- Persistence ---

    private boolean saveDocument(final ReviewFeedbackDocument document) {
        try {
            esStorageManager.save(INDEX_REFERENCE, document.documentId(), document);
            return true;
        } catch (Exception e) {
            log.error("Failed to save review feedback document id={}", document.documentId(), e);
            return false;
        }
    }

    // --- Query ---

    @Override
    public List<ReviewFeedbackDocument> queryFeedback(final FeedbackQueryRequest request) {
        final Query query = ReviewFeedbackQueryBuilder.build(request);

        final List<Sort> sorts = List.of(
                FieldSort.builder().field(SORT_FIELD).order(SortOrder.DESC).build()
        );

        final var items = esStorageManager.searchEntities(
                INDEX_REFERENCE, query, REVIEW_FEEDBACK_TYPE_REF, sorts, request.getLimit(), request.getOffset());

        log.info("Queried {} feedback items for statuses={}, scope={}, targetPath={}, team={}",
                items.size(), request.getStatuses(), request.getScope(), request.getTargetPath(), request.getTeamName());

        return items;
    }

    @Override
    public Map<Long, ReviewFeedbackDocument> feedbackByNote(final long projectId, final long mrIid,
                                                            final List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Map.of();
        }
        final var query = BoolQuery.builder().filterClauses(List.of(
                TermQuery.builder().field("project.projectId").value(projectId).build(),
                TermQuery.builder().field("mrIid").value(mrIid).build(),
                TermsQuery.builder().field("noteId").values(noteIds).build()
        )).build();
        return esStorageManager.searchEntities(INDEX_REFERENCE, query, REVIEW_FEEDBACK_TYPE_REF, List.of(),
                        noteIds.size(), 0).stream()
                .collect(Collectors.toMap(ReviewFeedbackDocument::getNoteId, Function.identity(), (first, second) -> second));
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
