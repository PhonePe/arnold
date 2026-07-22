package com.phonepe.devx.arnold.server.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.commons.columbus.search.misc.result.Hit;
import com.phonepe.commons.columbus.search.misc.result.Result;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.PrefixQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.commons.columbus.search.sort.FieldSort;
import com.phonepe.commons.columbus.search.sort.Sort;
import com.phonepe.commons.columbus.search.sort.SortOrder;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.core.models.feedback.TargetScope;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument.ReviewFeedbackDocumentBuilder;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.Priority;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.arnold.server.elastic.ESStorageManager;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;


@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    private static final String CORE_REVIEWS_INDEX = "CORE_REVIEWS";
    private static final TypeReference<ReviewFeedbackDocument> REVIEW_FEEDBACK_TYPE_REF =
            new TypeReference<>() {};
    private static final List<AcceptanceStatus> DEFAULT_STATUSES =
            List.of(AcceptanceStatus.APPLIED, AcceptanceStatus.PARTIALLY_APPLIED);

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
                .commentCategory(comment.getCommentCategory());
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

    // --- Query ---

    @Override
    public List<ReviewFeedbackDocument> getFeedback(final FeedbackQueryParams params) {
        final var effectiveStatuses = (params.getStatuses() == null || params.getStatuses().isEmpty())
                ? DEFAULT_STATUSES : params.getStatuses();

        final Query query = buildFeedbackQuery(
                effectiveStatuses,
                params.getScope(),
                params.getTargetPath(),
                params.getTeamName(),
                params.getDaysBack(),
                params.getMinScore(),
                params.getMinPriority());

        final List<Sort> sorts = List.of(
                FieldSort.builder().field("score").order(SortOrder.DESC).build(),
                FieldSort.builder().field("ingestedAt").order(SortOrder.DESC).build()
        );

        final var searchResult = esStorageManager.search(
                CORE_REVIEWS_INDEX, query, REVIEW_FEEDBACK_TYPE_REF, sorts, params.getLimit(), 0);
        final var items = extractEntities(searchResult);

        log.info("Queried {} feedback items for statuses={}, scope={}, targetPath={}, team={}",
                items.size(), effectiveStatuses, params.getScope(), params.getTargetPath(), params.getTeamName());

        return items;
    }

    private <T> List<T> extractEntities(
            final com.phonepe.commons.columbus.storage.models.result.SearchResult<T> searchResult) {
        return Optional.ofNullable(searchResult)
                .map(com.phonepe.commons.columbus.storage.models.result.SearchResult::getResult)
                .map(Result::getHits)
                .map(hits -> hits.stream()
                        .map(Hit::getEntity)
                        .toList())
                .orElse(List.of());
    }

    private void addStatusFilter(final List<Query> clauses, final List<AcceptanceStatus> statuses) {
        clauses.add(TermsQuery.builder()
                .field("acceptanceStatus")
                .values(statuses.stream().map(AcceptanceStatus::getValue).toList())
                .build());
    }

    private void addScoreFilter(final List<Query> clauses, final double minScore) {
        if (minScore > 0) {
            clauses.add(RangeQuery.builder()
                    .field("score")
                    .from(minScore)
                    .includeLower(true)
                    .build());
        }
    }

    private void addTimeRangeFilter(final List<Query> clauses, final int daysBack) {
        final long cutoffMs = Instant.now().minus(daysBack, ChronoUnit.DAYS).toEpochMilli();
        clauses.add(RangeQuery.builder()
                .field("ingestedAt")
                .from(cutoffMs)
                .includeLower(true)
                .build());
    }

    private void addTeamFilter(final List<Query> clauses, final String teamName) {
        if (teamName != null && !teamName.isBlank()) {
            clauses.add(TermQuery.builder()
                    .field("project.teamId")
                    .value(teamName)
                    .build());
        }
    }

    private void addPriorityFilter(final List<Query> clauses, final Priority minPriority) {
        if (minPriority != null && minPriority != Priority.LOW) {
            clauses.add(TermsQuery.builder()
                    .field("priority")
                    .values(java.util.Arrays.stream(Priority.values())
                            .filter(p -> p.getRank() <= minPriority.getRank())
                            .map(Priority::getValue)
                            .toList())
                    .build());
        }
    }

    private void addScopeFilter(final List<Query> clauses, final TargetScope scope, final String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            return;
        }

        final var effectiveScope = scope == null ? TargetScope.GROUP : scope;

        switch (effectiveScope) {
            case GROUP, SUBGROUP -> clauses.add(BoolQuery.builder()
                    .shouldClauses(List.of(
                            PrefixQuery.builder()
                                    .field("project.projectNamespace")
                                    .value(targetPath + "/")
                                    .build(),
                            TermQuery.builder()
                                    .field("project.projectNamespace")
                                    .value(targetPath)
                                    .build()))
                    .build());

            case PROJECT -> {
                final int lastSlash = targetPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    final String namespace = targetPath.substring(0, lastSlash);
                    final String projectName = targetPath.substring(lastSlash + 1);
                    clauses.add(TermQuery.builder()
                            .field("project.projectNamespace")
                            .value(namespace)
                            .build());
                    clauses.add(TermQuery.builder()
                            .field("project.projectName")
                            .value(projectName)
                            .build());
                } else {
                    throw new javax.ws.rs.BadRequestException(
                            "PROJECT scope requires a fully-qualified path (namespace/projectName), got: "
                                    + targetPath);
                }
            }
        }
    }

    private Query buildFeedbackQuery(
            final List<AcceptanceStatus> statuses,
            final TargetScope scope,
            final String targetPath,
            final String teamName,
            final int daysBack,
            final double minScore,
            final Priority minPriority) {

        final List<Query> filterClauses = new ArrayList<>();

        addStatusFilter(filterClauses, statuses);
        addScoreFilter(filterClauses, minScore);
        addTimeRangeFilter(filterClauses, daysBack);
        addScopeFilter(filterClauses, scope, targetPath);
        addTeamFilter(filterClauses, teamName);
        addPriorityFilter(filterClauses, minPriority);

        return BoolQuery.builder()
                .filterClauses(filterClauses)
                .build();
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

