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

package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.phonepe.commons.columbus.search.misc.result.Hit;
import com.phonepe.commons.columbus.search.misc.result.Result;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.commons.columbus.search.sort.FieldSort;
import com.phonepe.commons.columbus.search.sort.Sort;
import com.phonepe.commons.columbus.search.sort.SortOrder;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.arnold.core.guice.bindings.BindingNames;
import com.phonepe.arnold.core.services.ScmMetadataService;
import com.phonepe.arnold.core.services.HousekeepingService;
import com.phonepe.arnold.models.es.BackfillResult;
import com.phonepe.arnold.models.es.ProjectInfo;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.BackfillRequest;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.models.scm.Note;
import com.phonepe.arnold.models.scm.Project;
import com.phonepe.arnold.models.scm.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.phonepe.arnold.core.common.Constants.FIELD_MR_AUTHOR;
import static com.phonepe.arnold.core.common.Constants.FIELD_NOTE_CREATED_AT;
import static com.phonepe.arnold.core.common.Constants.FIELD_PROJECT_NAME;
import static com.phonepe.arnold.core.common.Constants.INDEX_REFERENCE;
import static com.phonepe.arnold.core.common.Constants.SORT_FIELD;
import static com.phonepe.arnold.core.utils.Utils.isBlank;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Re-enriches review-feedback documents that were persisted with missing SCM metadata enrichment.
 * Reuses the same ScmMetadataService client as live ingest, and the
 * same deterministic documentId upsert, so it is idempotent and safe to re-run.
 */
@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class HousekeepingServiceImpl implements HousekeepingService {
    /**
     * ES {@code from + size} paging cannot exceed {@code index.max_result_window} (default 10_000).
     * A real run drains the worklist as it fixes records, so its offset stays low; but a dry run
     * never removes records, so its offset grows unbounded. We stop before crossing this window and
     * report {@code drained=false} so the operator knows to page the remainder another way / re-run
     * a real pass. Kept just under the default to leave headroom for the final page.
     */
    private static final int MAX_SAFE_OFFSET = 10_000;

    private static final TypeReference<ReviewFeedbackDocument> DOC_TYPE = new TypeReference<>() {
    };

    private final ScmMetadataService scmMetadataService;
    private final ESStorageManager esStorageManager;
    @Named(BindingNames.ASYNC_TASK_EXECUTOR)
    private final ExecutorService asyncTaskExecutor;

    @Override
    public BackfillResult backfillReviewFeedback(final BackfillRequest request) {
        log.info("Starting review-feedback backfill: dryRun={}, pageSize={}, concurrency={}, maxPages={}, interPagePauseMs={}",
                request.isDryRun(), request.getPageSize(), request.getConcurrency(),
                request.getMaxPages(), request.getInterPagePauseMs());

        final Query worklist = buildWorklistQuery();
        final List<Sort> sorts = List.of(FieldSort.builder()
                .field(SORT_FIELD)
                .order(SortOrder.ASC)
                .build());
        final Semaphore inFlight = new Semaphore(request.getConcurrency());
        final BackfillState state = new BackfillState();

        // ES from+size paging. A record leaves the worklist ONLY when it is enriched AND written
        // (real run). Everything else - un-enrichable, failed, or enrichable-but-not-written
        // (dry run) - still matches the query, so we advance the offset past those to avoid
        // re-reading them. We advance by (pageSize - written-this-page): on a real run the offset
        // creeps forward only past the "stuck" records while fixed ones vacate their slots; on a
        // dry run nothing is written, so the offset advances by the full page (plain forward paging).
        while (processPage(request, worklist, sorts, inFlight, state) == PageOutcome.CONTINUE) {
            // Loop body intentionally empty; processPage advances state and signals when to stop.
        }

        final BackfillResult result = BackfillResult.builder()
                .dryRun(request.isDryRun())
                .scanned(state.scanned.get())
                .enriched(state.enriched.get())
                .enrichable(state.enrichable.get())
                .unEnrichableIds(new ArrayList<>(state.unEnrichableIds))
                .failedIds(new ArrayList<>(state.failedIds))
                .drained(state.drained)
                .build();
        if (request.isDryRun()) {
            log.info("Backfill dry-run finished (no writes performed): scanned={}, enrichable={}, "
                            + "unEnrichable={}, drained={}. Result: {}",
                    result.getScanned(), result.getEnrichable(), result.getUnEnrichableIds().size(),
                    result.isDrained(), result);
        } else {
            log.info("Backfill finished: {}", result);
        }
        return result;
    }

    /**
     * Advances one page: reads, enriches, updates {@code state}, and reports whether paging should
     * continue. Stops when the offset cap is hit, the worklist is drained, the max-page limit is
     * reached, or the inter-page pause is interrupted.
     */
    private PageOutcome processPage(final BackfillRequest request, final Query worklist,
            final List<Sort> sorts, final Semaphore inFlight, final BackfillState state) {
        if (offsetCapReached(state)) {
            return PageOutcome.STOP;
        }
        if (!enrichPage(request, worklist, sorts, inFlight, state)) {
            return PageOutcome.STOP;
        }
        if (maxPagesReached(request, state)) {
            return PageOutcome.STOP;
        }
        if (pauseInterrupted(request, state)) {
            return PageOutcome.STOP;
        }
        return PageOutcome.CONTINUE;
    }

    private boolean offsetCapReached(final BackfillState state) {
        final boolean capped = state.offset >= MAX_SAFE_OFFSET;
        if (capped) {
            log.warn("Backfill stopped at offset={} (>= max result window {}). Worklist not fully drained. "
                            + "Re-run to continue (a real run drains records so its offset stays low).",
                    state.offset, MAX_SAFE_OFFSET);
        }
        return capped;
    }

    /** Reads and enriches the next page. Returns {@code false} when the worklist is drained. */
    private boolean enrichPage(final BackfillRequest request, final Query worklist,
            final List<Sort> sorts, final Semaphore inFlight, final BackfillState state) {
        final int pageSize = Math.min(request.getPageSize(), MAX_SAFE_OFFSET - state.offset);
        final List<ReviewFeedbackDocument> page = readPage(worklist, sorts, pageSize, state.offset);
        if (page.isEmpty()) {
            state.drained = true;
            return false;
        }

        state.scanned.addAndGet(page.size());
        final int enrichedBefore = state.enriched.get();

        final List<CompletableFuture<Void>> futures = new ArrayList<>(page.size());
        for (final ReviewFeedbackDocument doc : page) {
            futures.add(processDocument(doc, request.isDryRun(), inFlight,
                    state.enriched, state.enrichable, state.unEnrichableIds, state.failedIds));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        // Records written this page leave the worklist; the rest stay and must be skipped past.
        final int writtenThisPage = state.enriched.get() - enrichedBefore;
        state.offset += page.size() - writtenThisPage;
        state.pagesProcessed++;
        log.info("Backfill page done: offset={}, scanned={}, enriched={}, enrichable={}, "
                        + "unEnrichable={}, failed={}",
                state.offset, state.scanned.get(), state.enriched.get(), state.enrichable.get(),
                state.unEnrichableIds.size(), state.failedIds.size());
        return true;
    }

    private boolean maxPagesReached(final BackfillRequest request, final BackfillState state) {
        return request.getMaxPages() > 0 && state.pagesProcessed >= request.getMaxPages();
    }

    private boolean pauseInterrupted(final BackfillRequest request, final BackfillState state) {
        final boolean interrupted = !pause(request.getInterPagePauseMs());
        if (interrupted) {
            log.warn("Backfill interrupted during inter-page pause; stopping after {} page(s).",
                    state.pagesProcessed);
        }
        return interrupted;
    }

    private enum PageOutcome {
        CONTINUE,
        STOP
    }

    /** Mutable state carried across backfill pages. */
    private static final class BackfillState {
        private final AtomicInteger scanned = new AtomicInteger();
        private final AtomicInteger enriched = new AtomicInteger();
        private final AtomicInteger enrichable = new AtomicInteger();
        private final List<String> unEnrichableIds = Collections.synchronizedList(new ArrayList<>());
        private final List<String> failedIds = Collections.synchronizedList(new ArrayList<>());
        private int offset = 0;
        private int pagesProcessed = 0;
        private boolean drained = false;
    }

    /**
     * Union of missing-enrichment conditions: any document missing note details, project name, or
     * MR-author. Time-independent, since enrichment failures can occur outside the outage window too.
     */
    private Query buildWorklistQuery() {
        return BoolQuery.builder()
                .minimumMatch("1")
                .should(missing(FIELD_NOTE_CREATED_AT))
                .should(missing(FIELD_PROJECT_NAME))
                .should(missing(FIELD_MR_AUTHOR))
                .build();
    }

    private Query missing(final String field) {
        return BoolQuery.builder()
                .mustNot(FieldExistsQuery.builder().field(field).build())
                .build();
    }

    private List<ReviewFeedbackDocument> readPage(final Query query, final List<Sort> sorts,
                                                  final int pageSize, final int offset) {
        final var searchResult = esStorageManager.search(INDEX_REFERENCE, query, DOC_TYPE, sorts, pageSize, offset);
        return Optional.ofNullable(searchResult)
                .map(SearchResult::getResult)
                .map(Result::getHits)
                .stream()
                .flatMap(Collection::stream)
                .map(Hit::getEntity)
                .filter(Objects::nonNull)
                .toList();
    }

    private CompletableFuture<Void> processDocument(final ReviewFeedbackDocument doc,
                                                    final boolean dryRun,
                                                    final Semaphore inFlight,
                                                    final AtomicInteger enriched,
                                                    final AtomicInteger enrichable,
                                                    final List<String> unEnrichableIds,
                                                    final List<String> failedIds) {
        final String documentId = doc.documentId();
        try {
            inFlight.acquire();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            failedIds.add(documentId);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture
                .supplyAsync(() -> reEnrich(doc), asyncTaskExecutor)
                .thenAccept(enrichedDoc -> {
                    if (enrichedDoc == null) {
                        // Nothing resolvable from SCM metadata - record stays incomplete, report for review.
                        unEnrichableIds.add(documentId);
                        return;
                    }
                    enrichable.incrementAndGet();
                    if (!dryRun) {
                        esStorageManager.save(INDEX_REFERENCE, enrichedDoc.documentId(), enrichedDoc);
                        enriched.incrementAndGet();
                    }
                })
                .exceptionally(ex -> {
                    // Covers SCM metadata resolution errors and (on a real run) ES upsert failures. The record
                    // is left untouched in ES; re-running the backfill retries it (idempotent upsert).
                    log.error("Backfill failed for documentId={}", documentId, ex);
                    failedIds.add(documentId);
                    return null;
                })
                .whenComplete((v, t) -> inFlight.release());
    }

    /**
     * Re-enriches only the fields that are missing. Returns a document with newly-resolved data, or
     * null if nothing could be resolved (the record stays un-enrichable and is reported).
     */
    private ReviewFeedbackDocument reEnrich(final ReviewFeedbackDocument doc) {
        final var builder = doc.toBuilder();
        boolean resolvedSomething = false;

        final ProjectInfo existingProject = doc.getProject();
        final Long projectId = existingProject != null ? existingProject.getProjectId() : null;

        // Note details
        if (isBlank(doc.getNoteCreatedAt()) && doc.getNoteId() != null
                && projectId != null && doc.getMrIid() != null) {
            final Optional<Note> note = scmMetadataService.getMergeRequestNote(
                    projectId.intValue(), doc.getMrIid().intValue(), doc.getNoteId().intValue());
            if (note.isPresent()) {
                ReviewFeedbackDocumentMapper.applyNoteDetails(builder, doc.getNoteId(), doc.getDiscussionId(), note);
                resolvedSomething = true;
            }
        }

        // Project details (rebuild nested ProjectInfo only if we fetch a fresh Project)
        final boolean projectMissing = existingProject == null || isBlank(existingProject.getProjectName());
        if (projectMissing && projectId != null) {
            final Optional<Project> project = scmMetadataService.getProject(projectId.intValue());
            if (project.isPresent()) {
                builder.project(ReviewFeedbackDocumentMapper.buildProjectInfo(projectId, project));
                resolvedSomething = true;
            }
        }

        // MR-author details (independent of project)
        if (doc.getMrAuthor() == null && doc.getMrAuthorId() != null) {
            final Optional<User> mrAuthor = scmMetadataService.getUser(doc.getMrAuthorId().intValue());
            if (mrAuthor.isPresent()) {
                builder.mrAuthor(mrAuthor.get());
                resolvedSomething = true;
            }
        }

        return resolvedSomething ? builder.build() : null;
    }

    /**
     * Sleeps for the given duration. Returns {@code true} if the pause completed, {@code false} if
     * the thread was interrupted (in which case the caller should stop the run).
     */
    private boolean pause(final long ms) {
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
