/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import com.phonepe.columbus.dao.id.ValueId;
import com.phonepe.columbus.search.misc.result.Hit;
import com.phonepe.columbus.search.misc.result.Result;
import com.phonepe.columbus.storage.models.result.SearchResult;
import com.phonepe.arnold.core.services.ScmMetadataService;
import com.phonepe.arnold.models.es.BackfillResult;
import com.phonepe.arnold.models.es.ProjectInfo;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.BackfillRequest;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.models.scm.Note;
import com.phonepe.arnold.models.scm.Project;
import com.phonepe.arnold.models.scm.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HousekeepingServiceImplTest {

    @Mock
    private ScmMetadataService scmMetadataService;

    @Mock
    private ESStorageManager esStorageManager;

    private HousekeepingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HousekeepingServiceImpl(scmMetadataService, esStorageManager,
                Executors.newCachedThreadPool());
    }

    private static BackfillRequest request(final boolean dryRun) {
        return BackfillRequest.builder()
                .dryRun(dryRun)
                .pageSize(500)
                .concurrency(4)
                .interPagePauseMs(0)
                .maxPages(0)
                .build();
    }

    private static ReviewFeedbackDocument docMissingNote(final long noteId) {
        return ReviewFeedbackDocument.builder()
                .mrId(20L)
                .mrIid(30L)
                .mrAuthorId(40L)
                .mrAuthor(User.builder().id(40).build())
                .project(ProjectInfo.builder().projectId(10L).projectName("proj").build())
                .noteId(noteId)
                .discussionId("disc-" + noteId)
                .noteCreatedAt(null)
                .build();
    }

    private static ReviewFeedbackDocument docMissingEverything(final long noteId) {
        return ReviewFeedbackDocument.builder()
                .mrId(20L)
                .mrIid(30L)
                .mrAuthorId(40L)
                .mrAuthor(null)
                .project(ProjectInfo.builder().projectId(10L).build())
                .noteId(noteId)
                .noteCreatedAt(null)
                .build();
    }

    private static Note note(final long noteId) {
        return Note.builder()
                .id((int) noteId)
                .createdAt("2026-04-27T10:00:00Z")
                .body("body " + noteId)
                .author(User.builder().id(100).build())
                .build();
    }

    private static Project project() {
        return Project.builder().id(10).name("test-project").namespace("acme").pod("pod").teamId("t1").build();
    }

    private static Hit<ReviewFeedbackDocument> hit(final ReviewFeedbackDocument doc) {
        return Hit.<ReviewFeedbackDocument>builder()
                .id(ValueId.builder().value(doc.documentId()).build())
                .entity(doc)
                .build();
    }

    /**
     * Stubs paged search: returns the given documents split into pages of {@code pageSize} using the
     * offset the service asks for. Mirrors ES from+size semantics against a static list (dry-run style).
     */
    private void stubStaticPages(final List<ReviewFeedbackDocument> all, final int pageSize) {
        when(esStorageManager.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    final int size = inv.getArgument(4);
                    final int offset = inv.getArgument(5);
                    final List<Hit<ReviewFeedbackDocument>> hits = new ArrayList<>();
                    for (int i = offset; i < Math.min(offset + size, all.size()); i++) {
                        hits.add(hit(all.get(i)));
                    }
                    return searchResult(hits);
                });
    }

    private static SearchResult<ReviewFeedbackDocument> searchResult(final List<Hit<ReviewFeedbackDocument>> hits) {
        return SearchResult.<ReviewFeedbackDocument>builder()
                .result(Result.<ReviewFeedbackDocument>builder().hits(hits).build())
                .build();
    }

    /**
     * Stubs a paged search backed by a mutable, self-draining worklist: {@code save} removes the
     * matching document so a later page no longer returns it. Needed for any real-run scenario where
     * every document on a page is expected to be written, since the offset then stays put and the
     * loop relies on the backing store actually reflecting the write on the next read (unlike
     * {@link #stubStaticPages}, which serves a fixed, never-shrinking list).
     */
    private void stubDrainingWorklist(final List<ReviewFeedbackDocument> worklist) {
        when(esStorageManager.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    final int size = inv.getArgument(4);
                    final int offset = inv.getArgument(5);
                    final List<Hit<ReviewFeedbackDocument>> hits = new ArrayList<>();
                    synchronized (worklist) {
                        for (int i = offset; i < Math.min(offset + size, worklist.size()); i++) {
                            hits.add(hit(worklist.get(i)));
                        }
                    }
                    return searchResult(hits);
                });
        org.mockito.Mockito.doAnswer(inv -> {
            final String id = inv.getArgument(1);
            worklist.removeIf(d -> d.documentId().equals(id));
            return null;
        }).when(esStorageManager).save(any(), any(), any());
    }

    @Test
    void testBackfillReviewFeedback_shouldCountEnrichableAndNotWrite_whenDryRun() {
        final List<ReviewFeedbackDocument> all = List.of(docMissingNote(1L), docMissingNote(2L));
        stubStaticPages(all, 500);
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> Optional.of(note((int) inv.getArgument(2))));

        final BackfillResult result = service.backfillReviewFeedback(request(true));

        assertTrue(result.isDryRun());
        assertEquals(2, result.getScanned());
        assertEquals(2, result.getEnrichable());
        assertEquals(0, result.getEnriched());
        assertTrue(result.isDrained());
        verify(esStorageManager, never()).save(any(), any(), any());
    }

    @Test
    void testBackfillReviewFeedback_shouldReEnrichNoteAndUpsert_whenRealRun() {
        // Real run drains the worklist as records are fixed: a saved doc no longer matches the query.
        // Model that with a synchronized worklist that search reads (snapshot) and save removes from.
        final List<ReviewFeedbackDocument> worklist =
                Collections.synchronizedList(new ArrayList<>(List.of(docMissingNote(1L), docMissingNote(2L))));
        stubDrainingWorklist(worklist);
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> Optional.of(note((int) inv.getArgument(2))));

        final BackfillResult result = service.backfillReviewFeedback(request(false));

        assertFalse(result.isDryRun());
        assertEquals(2, result.getEnriched());
        assertEquals(2, result.getEnrichable());
        assertTrue(result.getUnEnrichableIds().isEmpty());
        assertTrue(result.getFailedIds().isEmpty());
        assertTrue(result.isDrained());
        verify(esStorageManager, verifyTimes(2)).save(eq("CORE_REVIEWS"), any(), any());
    }

    @Test
    void testBackfillReviewFeedback_shouldReportUnEnrichable_whenScmMetadataReturnsEmpty() {
        stubStaticPages(List.of(docMissingNote(1L)), 500);
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());

        final BackfillResult result = service.backfillReviewFeedback(request(false));

        assertEquals(1, result.getScanned());
        assertEquals(0, result.getEnriched());
        assertEquals(List.of("10:20:1"), result.getUnEnrichableIds());
        verify(esStorageManager, never()).save(any(), any(), any());
    }

    @Test
    void testBackfillReviewFeedback_shouldReportFailed_whenSaveThrows() {
        stubStaticPages(List.of(docMissingNote(1L)), 500);
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.of(note(1L)));
        doThrow(new RuntimeException("ES down")).when(esStorageManager).save(any(), any(), any());

        final BackfillResult result = service.backfillReviewFeedback(request(false));

        assertEquals(1, result.getScanned());
        assertEquals(0, result.getEnriched());
        assertEquals(List.of("10:20:1"), result.getFailedIds());
    }

    @Test
    void testBackfillReviewFeedback_shouldFillProjectAndAuthor_whenBothMissing() {
        // This document is fully enrichable and gets written in a single page; the mock worklist
        // must reflect that removal, otherwise the offset (which stays put once a page is fully
        // written) would keep re-serving the same already-fixed document forever.
        stubDrainingWorklist(Collections.synchronizedList(new ArrayList<>(List.of(docMissingEverything(1L)))));
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(note(1L)));
        when(scmMetadataService.getProject(10)).thenReturn(Optional.of(project()));
        when(scmMetadataService.getUser(40)).thenReturn(Optional.of(User.builder().id(40).name("author").build()));

        final ArgumentCaptor<ReviewFeedbackDocument> captor = ArgumentCaptor.forClass(ReviewFeedbackDocument.class);
        final BackfillResult result = service.backfillReviewFeedback(request(false));

        assertEquals(1, result.getEnriched());
        verify(esStorageManager).save(eq("CORE_REVIEWS"), eq("10:20:1"), captor.capture());
        final ReviewFeedbackDocument saved = captor.getValue();
        assertEquals("test-project", saved.getProject().getProjectName());
        assertEquals("author", saved.getMrAuthor().getName());
        assertEquals("2026-04-27T10:00:00Z", saved.getNoteCreatedAt());
    }

    @Test
    void testBackfillReviewFeedback_shouldKeepPagingUntilTrulyEmpty_whenShortNonEmptyPage() {
        // Simulates a page shorter than the requested pageSize that is NOT actually the last one
        // (e.g. MAX_SAFE_OFFSET-capped size, or a transient view of a mutating worklist). The service
        // must keep paging past it and only report drained once a genuinely empty page is returned.
        final List<SearchResult<ReviewFeedbackDocument>> pages = List.of(
                searchResult(List.of(hit(docMissingNote(1L)))),
                searchResult(List.of(hit(docMissingNote(2L)))),
                searchResult(List.of()));
        final java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        when(esStorageManager.search(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(inv -> pages.get(Math.min(callCount.getAndIncrement(), pages.size() - 1)));
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> Optional.of(note((int) inv.getArgument(2))));

        final BackfillResult result = service.backfillReviewFeedback(request(true));

        assertEquals(2, result.getScanned());
        assertEquals(2, result.getEnrichable());
        assertTrue(result.isDrained());
        verify(esStorageManager, verifyTimes(3)).search(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void testBackfillReviewFeedback_shouldStopEarlyAndReportNotDrained_whenMaxPagesReached() {
        final List<ReviewFeedbackDocument> all = List.of(docMissingNote(1L), docMissingNote(2L), docMissingNote(3L));
        stubStaticPages(all, 1); // 1 doc per page
        when(scmMetadataService.getMergeRequestNote(anyInt(), anyInt(), anyInt()))
                .thenAnswer(inv -> Optional.of(note((int) inv.getArgument(2))));

        final BackfillRequest req = BackfillRequest.builder()
                .dryRun(true).pageSize(1).concurrency(1).interPagePauseMs(0).maxPages(2).build();
        final BackfillResult result = service.backfillReviewFeedback(req);

        assertEquals(2, result.getScanned());
        assertFalse(result.isDrained());
    }

    // --- helpers ---

    private static org.mockito.verification.VerificationMode verifyTimes(final int n) {
        return org.mockito.Mockito.times(n);
    }
}
