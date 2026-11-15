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

import com.phonepe.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProvider;
import com.phonepe.arnold.core.provider.VersionControlProviders;
import com.phonepe.arnold.core.services.ReviewFeedbackService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.models.comment.CommentResponse;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.AcceptanceStatus;
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
                analysisRunStore, new com.phonepe.arnold.core.ArnoldCoreConfiguration());
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

    @Test
    void testList_shouldClassifyReviewSource() {
        final var persona = new PublishedComment("7", 7L, null, "d-7", 1L, "arnold-bot", "Leak", true, false, false,
                "published", 1L, 2L, null, List.of(), new PersonaAttribution("run-1", "netra", 3));
        final var general = new PublishedComment("8", 8L, null, "d-8", 1L, "arnold-bot", "Nit", true, false, false,
                "published", 1L, 2L, null, List.of());
        final var human = new PublishedComment("9", 9L, null, "d-9", 2L, "reviewer", "Why?", true, false, false,
                "published", 1L, 2L, null, List.of());
        final var fromFeedback = new PublishedComment("10", 10L, null, "d-10", 3L, "bot", "Retry", true, false,
                false, "published", 1L, 2L, null, List.of());
        final var assessedHuman = new PublishedComment("11", 11L, null, "d-11", 2L, "reviewer", "Stale lock?", true,
                false, false, "published", 1L, 2L, null, List.of());
        when(provider.listDiscussions(10L, 12L, "user-1"))
                .thenReturn(List.of(persona, general, human, fromFeedback, assessedHuman));
        when(reviewFeedbackService.feedbackByNote(10L, 12L, List.of(7L, 8L, 9L, 10L, 11L))).thenReturn(Map.of(10L,
                ReviewFeedbackDocument.builder().noteId(10L).analysisRunId("run-1").personaId("shinrai")
                        .personaVersion(1L).build(),
                11L, ReviewFeedbackDocument.builder().noteId(11L).acceptanceStatus(AcceptanceStatus.APPLIED).build()));
        when(analysisRunStore.get("run-1")).thenReturn(Optional.of(new AnalysisRun("run-1", "gitlab:10:12",
                List.of("improve"), "user-1", "completed", Map.of(), null, null,
                List.of(new ReviewPersonaSnapshot("netra", "Netra", "Check security.", 3),
                        new ReviewPersonaSnapshot("shinrai", "Shinrai", "Check reliability.", 1)), null, 1L, 2L)));

        final var result = service.list(VersionControlProviderType.GITLAB, 10L, 12L, "user-1");

        assertEquals(List.of(CommentResponse.SOURCE_PERSONA, CommentResponse.SOURCE_AI, CommentResponse.SOURCE_HUMAN,
                CommentResponse.SOURCE_PERSONA, CommentResponse.SOURCE_HUMAN),
                result.stream().map(CommentResponse::reviewSource).toList());
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
