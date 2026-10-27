package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentServiceImplTest {
    private final AerospikeCommentStore commentStore = mock(AerospikeCommentStore.class);
    private final VersionControlProviders providers = mock(VersionControlProviders.class);
    private final VersionControlProvider provider = mock(VersionControlProvider.class);
    private final VersionControlSyncService syncService = mock(VersionControlSyncService.class);
    private final ReviewFeedbackService reviewFeedbackService = mock(ReviewFeedbackService.class);
    private final AerospikeAnalysisRunStore analysisRunStore = mock(AerospikeAnalysisRunStore.class);
    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentStore, providers, syncService, reviewFeedbackService,
                analysisRunStore);
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(provider);
        when(commentStore.list()).thenReturn(List.of());
        when(provider.listDrafts(10L, 12L, "user-1")).thenReturn(List.of());
    }

    @Test
    void testList_shouldAttachAssessment_whenFeedbackExists() {
        when(provider.listDiscussions(10L, 12L, "user-1")).thenReturn(List.of(comment()));
        when(reviewFeedbackService.feedbackByNote(10L, 12L, List.of(7L))).thenReturn(Map.of(7L,
                ReviewFeedbackDocument.builder().noteId(7L).acceptanceStatus(AcceptanceStatus.APPLIED)
                        .assessedSha("a".repeat(40)).reasoning("The requested guard is present.").build()));

        final var result = service.list(10L, 12L, "user-1");

        assertEquals(AcceptanceStatus.APPLIED, result.get(0).acceptanceStatus());
        assertEquals("a".repeat(40), result.get(0).assessedSha());
        assertEquals("The requested guard is present.", result.get(0).assessmentReasoning());
    }

    @Test
    void testList_shouldReturnCommentsWithoutAssessment_whenFeedbackLookupFails() {
        when(provider.listDiscussions(10L, 12L, "user-1")).thenReturn(List.of(comment()));
        when(reviewFeedbackService.feedbackByNote(10L, 12L, List.of(7L)))
                .thenThrow(new RuntimeException("Elasticsearch unavailable"));

        final var result = service.list(10L, 12L, "user-1");

        assertNull(result.get(0).acceptanceStatus());
    }

    @Test
    void testList_shouldClassifyReviewSource() {
        final var persona = new PublishedComment("7", 7L, null, "d-7", 1L, "cibot", "Leak", true, false, false,
                "published", 1L, 2L, null, List.of(), new PersonaAttribution("run-1", "netra", 3));
        final var general = new PublishedComment("8", 8L, null, "d-8", 1L, "cibot", "Nit", true, false, false,
                "published", 1L, 2L, null, List.of());
        final var human = new PublishedComment("9", 9L, null, "d-9", 2L, "reviewer", "Why?", true, false, false,
                "published", 1L, 2L, null, List.of());
        final var fromFeedback = new PublishedComment("10", 10L, null, "d-10", 3L, "bot", "Retry", true, false,
                false, "published", 1L, 2L, null, List.of());
        when(provider.listDiscussions(10L, 12L, "user-1")).thenReturn(List.of(persona, general, human, fromFeedback));
        when(reviewFeedbackService.feedbackByNote(10L, 12L, List.of(7L, 8L, 9L, 10L))).thenReturn(Map.of(10L,
                ReviewFeedbackDocument.builder().noteId(10L).analysisRunId("run-1").personaId("shinrai")
                        .personaVersion(1L).build()));
        when(analysisRunStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:10:12",
                List.of("improve"), "user-1", "completed", Map.of(), null, null,
                List.of(new ReviewPersonaSnapshot("netra", "Netra", "Check security.", 3),
                        new ReviewPersonaSnapshot("shinrai", "Shinrai", "Check reliability.", 1)), null, 1L, 2L)));

        final var result = service.list(10L, 12L, "user-1");

        assertEquals(List.of(CommentResponse.SOURCE_PERSONA, CommentResponse.SOURCE_AI, CommentResponse.SOURCE_HUMAN,
                CommentResponse.SOURCE_PERSONA), result.stream().map(CommentResponse::reviewSource).toList());
        assertEquals("netra", result.get(0).personaId());
        assertEquals(3L, result.get(0).personaVersion());
        assertEquals("Netra", result.get(0).personaName());
        assertEquals("run-1", result.get(0).analysisRunId());
        assertEquals("Shinrai", result.get(3).personaName());
        assertNull(result.get(1).personaId());
    }

    private PublishedComment comment() {
        return new PublishedComment("7", 7L, null, "discussion-7", 1L, "reviewer", "Fix this", true,
                false, false, "published", 1L, 2L, null, List.of());
    }
}
