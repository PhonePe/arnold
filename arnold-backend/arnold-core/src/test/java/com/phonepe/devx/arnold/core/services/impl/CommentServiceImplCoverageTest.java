package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceImplCoverageTest {
    private static final String MR_ID = "gitlab:10:12";
    private final AerospikeCommentStore commentStore = mock(AerospikeCommentStore.class);
    private final VersionControlProviders providers = mock(VersionControlProviders.class);
    private final VersionControlProvider provider = mock(VersionControlProvider.class);
    private final VersionControlSyncService syncService = mock(VersionControlSyncService.class);
    private final ReviewFeedbackService feedbackService = mock(ReviewFeedbackService.class);
    private final AerospikeAnalysisRunStore runStore = mock(AerospikeAnalysisRunStore.class);
    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentStore, providers, syncService, feedbackService, runStore);
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(provider);
        when(commentStore.list()).thenReturn(List.of());
    }

    private static PublishedComment published(final long id, final long createdAt) {
        return new PublishedComment("p" + id, id, null, "d" + id, 1L, "reviewer", "body", true, false, false,
                "published", createdAt, createdAt, null, null);
    }

    private static PublishedComment draft(final long draftId, final long createdAt) {
        return new PublishedComment("draft-" + draftId, 0, draftId, null, 1L, "me", "draft", false, false, true,
                "draft", createdAt, createdAt, null, List.of());
    }

    private static CreateDiscussionRequest request(final String discussionId) {
        return CreateDiscussionRequest.builder().provider(CreateDiscussionRequest.VersionControlProvider.GITLAB)
                .projectId(10).mrIid(12).body("hello").clientOperationId("op1").discussionId(discussionId).build();
    }

    @Test
    void listMergesDraftsSortedAndMarksMissingNotesDeleted() {
        final var stale = new CommentRecord("gitlab:10:12:99", VersionControlProviderType.GITLAB, MR_ID, "d99", 99L,
                1L, "u", "old", "published", true, false, false, false, "gitlab", 1L, 1L, 1L, null, List.of());
        final var alreadyDeleted = new CommentRecord("gitlab:10:12:98", VersionControlProviderType.GITLAB, MR_ID, "d98",
                98L, 1L, "u", "old", "published", true, false, false, true, "gitlab", 1L, 1L, 1L, null, List.of());
        final var otherMr = new CommentRecord("gitlab:10:13:97", VersionControlProviderType.GITLAB, "gitlab:10:13", "d97",
                97L, 1L, "u", "old", "published", true, false, false, false, "gitlab", 1L, 1L, 1L, null, List.of());
        when(commentStore.list()).thenReturn(List.of(stale, alreadyDeleted, otherMr));
        when(provider.listDiscussions(10L, 12L, "u")).thenReturn(List.of(published(7, 30)));
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of(draft(5, 10)));
        when(feedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of());

        final var result = service.list(10L, 12L, "u");

        assertEquals(List.of(5L, 0L), result.stream().map(r -> r.draftId() == null ? 0L : r.draftId()).toList());
        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(syncService, org.mockito.Mockito.times(2)).synchronize(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(r -> r.noteId() == 99L && r.deleted()));
        assertEquals(CommentResponse.SOURCE_HUMAN, result.get(1).reviewSource());
        assertEquals(List.of(), result.get(1).suggestions());
    }

    @Test
    void listAttributesPersonaFromFeedbackAndToleratesRunStoreFailure() {
        when(provider.listDiscussions(10L, 12L, "u")).thenReturn(List.of(published(7, 1)));
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of());
        when(feedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of(7L,
                ReviewFeedbackDocument.builder().noteId(7L).analysisRunId("run").personaId("netra").build(),
                8L, ReviewFeedbackDocument.builder().noteId(8L).personaId(" ").build()));
        when(runStore.get("run")).thenThrow(new RuntimeException("down"));

        final var r = service.list(10L, 12L, "u").get(0);

        assertEquals("netra", r.personaId());
        assertEquals(0L, r.personaVersion());
        assertEquals("run", r.analysisRunId());
        assertNull(r.personaName());
    }

    @Test
    void listUsesFeedbackRunIdWithoutPersonaAndIgnoresBlankPersonaId() {
        when(provider.listDiscussions(10L, 12L, "u")).thenReturn(List.of(published(7, 1)));
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of());
        when(feedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of(7L,
                ReviewFeedbackDocument.builder().noteId(7L).analysisRunId("run").personaId("").build()));

        final var r = service.list(10L, 12L, "u").get(0);

        assertNull(r.personaId());
        assertEquals("run", r.analysisRunId());
    }

    @Test
    void listDraftsMapsProviderDrafts() {
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of(draft(5, 10)));
        final var result = service.listDrafts(10L, 12L, "u");
        assertEquals(1, result.size());
        assertEquals(MR_ID, result.get(0).mergeRequestId());
        assertEquals(5L, result.get(0).draftId());
    }

    @Test
    void createReturnsExistingOperationWithoutCallingProvider() {
        final var existing = new CommentRecord("operation:op1", VersionControlProviderType.GITLAB, MR_ID, null, 0, null,
                null, "hello", "pending", false, false, true, false, "arnold_user", 1, 1, 1, null, null,
                new PersonaAttribution("r", "p", 2));
        when(commentStore.get("operation:op1")).thenReturn(Optional.of(existing));

        final var r = service.create(request(null), "u");

        assertEquals("pending", r.state());
        assertEquals(List.of(), r.suggestions());
        assertEquals(CommentResponse.SOURCE_PERSONA, r.reviewSource());
        verify(provider, never()).createDiscussion(any(), any());
    }

    @Test
    void createPersistsCanonicalRecordAndDeletesPending() {
        when(commentStore.get("operation:op1")).thenReturn(Optional.empty());
        when(provider.createDiscussion(any(), any())).thenReturn(published(7, 5));

        final var r = service.create(request(null), "u");

        assertEquals("published", r.state());
        assertEquals(7L, r.noteId());
        verify(commentStore, org.mockito.Mockito.times(2)).create(any(CommentRecord.class));
        verify(commentStore).delete("operation:op1");
    }

    @Test
    void createMarksPendingUnknownAndRethrowsOnProviderFailure() {
        when(commentStore.get("operation:op1")).thenReturn(Optional.empty());
        when(provider.createDiscussion(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> service.create(request("disc"), "u"));

        final var captor = ArgumentCaptor.forClass(CommentRecord.class);
        verify(commentStore).update(captor.capture());
        assertEquals("unknown", captor.getValue().state());
        assertEquals("operation:op1", captor.getValue().id());
        assertEquals(false, captor.getValue().individualNote());
    }

    @Test
    void draftOperationsDelegateToProvider() {
        final var req = request(null);
        when(provider.createDraft(req, "u")).thenReturn(draft(5, 1));
        assertEquals(5L, service.createDraft(req, "u").draftId());

        final var update = UpdateDraftRequest.builder().projectId(10).mrIid(12).body("new").build();
        when(provider.updateDraft(5L, update, "u")).thenReturn(draft(5, 1));
        assertEquals(MR_ID, service.updateDraft(5L, update, "u").mergeRequestId());

        service.deleteDraft(5L, 10L, 12L, "u");
        verify(provider).deleteDraft(5L, 10L, 12L, "u");
    }

    @Test
    void publishDraftsPublishesThenReturnsRefreshedList() {
        final var req = PublishReviewRequest.builder().projectId(10).mrIid(12).reviewerState("reviewed").build();
        when(provider.listDiscussions(10L, 12L, "u")).thenReturn(List.of(published(7, 1)));
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of());
        when(feedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of());

        assertEquals(1, service.publishDrafts(req, "u").size());
        verify(provider).publishDrafts(req, "u");
    }

    @Test
    void resolveAndApplySuggestionDelegate() {
        final var req = ResolveDiscussionRequest.builder().projectId(10).mrIid(12).resolved(true).build();
        service.resolveDiscussion("d1", req, "u");
        service.applySuggestion(3L, "msg", "u");
        verify(provider).resolveDiscussion("d1", req, "u");
        verify(provider).applySuggestion(3L, "msg", "u");
    }

    @Test
    void personaNamesAreResolvedFromRunSnapshot() {
        final var withPersona = new PublishedComment("p7", 7, null, "d7", 1L, "cibot", "b", true, false, false,
                "published", 1, 1, null, List.of(), new PersonaAttribution("run", "netra", 3));
        when(provider.listDiscussions(10L, 12L, "u")).thenReturn(List.of(withPersona));
        when(provider.listDrafts(10L, 12L, "u")).thenReturn(List.of());
        when(feedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of());
        when(runStore.get("run")).thenReturn(Optional.empty());

        assertNull(service.list(10L, 12L, "u").get(0).personaName());
        verify(feedbackService).feedbackByNote(10L, 12L, List.of(7L));
    }
}
