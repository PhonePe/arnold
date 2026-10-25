package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.core.provider.VersionControlProviders;
import com.phonepe.arnold.core.services.ReviewFeedbackService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.AcceptanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(commentStore, providers, syncService, reviewFeedbackService);
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

        final var result = service.list(VersionControlProviderType.GITLAB, 10L, 12L, "user-1");

        assertEquals(AcceptanceStatus.APPLIED, result.get(0).acceptanceStatus());
        assertEquals("a".repeat(40), result.get(0).assessedSha());
        assertEquals("The requested guard is present.", result.get(0).assessmentReasoning());
    }

    @Test
    void testList_shouldReturnCommentsWithoutAssessment_whenFeedbackLookupFails() {
        when(provider.listDiscussions(10L, 12L, "user-1")).thenReturn(List.of(comment()));
        when(reviewFeedbackService.feedbackByNote(10L, 12L, List.of(7L)))
                .thenThrow(new RuntimeException("Elasticsearch unavailable"));

        final var result = service.list(VersionControlProviderType.GITLAB, 10L, 12L, "user-1");

        assertNull(result.get(0).acceptanceStatus());
    }

    private PublishedComment comment() {
        return new PublishedComment("7", 7L, null, "discussion-7", 1L, "reviewer", "Fix this", true,
                false, false, "published", 1L, 2L, null, List.of());
    }
}
