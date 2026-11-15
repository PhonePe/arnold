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

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.model.comment.CommentRecord;
import com.phonepe.arnold.core.model.persona.PersonaAttribution;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.provider.VersionControlProviders;
import com.phonepe.arnold.core.services.CommentService;
import com.phonepe.arnold.core.services.ReviewFeedbackService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.models.comment.CommentResponse;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
@Slf4j
public class CommentServiceImpl implements CommentService {
    private static final String GITLAB_SOURCE = "gitlab";
    private final AerospikeCommentStore commentStore;
    private final VersionControlProviders providers;
    private final VersionControlSyncService syncService;
    private final ReviewFeedbackService reviewFeedbackService;
    private final AerospikeAnalysisRunStore analysisRunStore;
    private final String aiAuthor;

    @Inject
    public CommentServiceImpl(final AerospikeCommentStore commentStore,
                              final VersionControlProviders providers,
                              final VersionControlSyncService syncService,
                              final ReviewFeedbackService reviewFeedbackService,
                              final AerospikeAnalysisRunStore analysisRunStore,
                              final ArnoldCoreConfiguration configuration) {
        this.aiAuthor = configuration.getAnalysisConfiguration().getAiCommentAuthor();
        this.commentStore = commentStore;
        this.providers = providers;
        this.syncService = syncService;
        this.reviewFeedbackService = reviewFeedbackService;
        this.analysisRunStore = analysisRunStore;
    }

    @Override
    public List<CommentResponse> list(final VersionControlProviderType provider, final long projectId,
                                      final long mrIid, final String userId) {
        final var vcs = providers.get(provider);
        final var mergeRequestId = changeRequestId(provider, projectId, mrIid);
        final var published = vcs.listDiscussions(projectId, mrIid, userId);
        published.stream().map(comment -> toRecord(provider, comment, projectId, mrIid)).forEach(syncService::synchronize);
        reconcileDeleted(provider, mergeRequestId, published);
        final var drafts = vcs.listDrafts(projectId, mrIid, userId);
        final var feedback = feedback(projectId, mrIid, published);
        final var attributions = new HashMap<Long, PersonaAttribution>();
        published.forEach(comment -> {
            final var attribution = attribution(comment.persona(), feedback.get(comment.numericId()));
            if (attribution != null) {
                attributions.put(comment.numericId(), attribution);
            }
        });
        final var personaNames = personaNames(attributions.values());
        return Stream.concat(published.stream(), drafts.stream())
                .sorted(Comparator.comparingLong(PublishedComment::createdAt))
                .map(comment -> {
                    final var isPublished = comment.draftId() == null;
                    final var attribution = isPublished ? attributions.get(comment.numericId()) : null;
                    return toResponse(comment, mergeRequestId, isPublished ? feedback.get(comment.numericId()) : null,
                            attribution, attribution == null ? null : personaNames.get(personaKey(attribution)));
                })
                .toList();
    }

    @Override
    public List<CommentResponse> listDrafts(final VersionControlProviderType provider, final long projectId,
                                            final long mrIid, final String userId) {
        final var mergeRequestId = changeRequestId(provider, projectId, mrIid);
        return providers.get(provider).listDrafts(projectId, mrIid, userId).stream()
                .map(comment -> toResponse(comment, mergeRequestId))
                .toList();
    }

    @Override
    public CommentResponse create(final CreateDiscussionRequest request, final String userId) {
        final var pendingId = "operation:" + request.getClientOperationId();
        final var providerType = type(request.getProvider());
        final var changeRequestId = com.phonepe.arnold.core.model.vcs.ChangeRequestRecord.key(
                providerType, Long.toString(request.getProjectId()), request.getMrIid());
        final var existing = commentStore.get(pendingId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        final var now = System.currentTimeMillis();
        final var pending = new CommentRecord(pendingId, providerType, changeRequestId, null, 0,
                null, null, request.getBody(), "pending", false, false,
                request.getDiscussionId() == null && request.getPosition() == null, false, "arnold_user", now, now,
                now, request.getPosition(), List.of());
        commentStore.create(pending);
        try {
            final var published = providers.get(providerType).createDiscussion(request, userId);
            final var canonicalId = CommentRecord.key(providerType, Long.toString(request.getProjectId()),
                    request.getMrIid(), published.numericId());
            final var completed = new CommentRecord(canonicalId, providerType, pending.changeRequestId(),
                    published.threadId(), published.numericId(), published.authorId(), published.authorUsername(),
                    published.body(), "published", published.resolvable(), published.resolved(),
                    published.individualNote(), false, pending.source(), published.createdAt(), published.updatedAt(),
                    System.currentTimeMillis(), published.position(), suggestions(published), published.persona());
            commentStore.create(completed);
            commentStore.delete(pendingId);
            return toResponse(completed);
        } catch (RuntimeException failure) {
            commentStore.update(new CommentRecord(pending.id(), providerType, pending.changeRequestId(), null, 0, null,
                    null, pending.body(), "unknown", false, false, pending.individualNote(), false, pending.source(),
                    now, now, System.currentTimeMillis(), pending.position(), List.of()));
            throw failure;
        }
    }

    @Override
    public CommentResponse createDraft(final CreateDiscussionRequest request, final String userId) {
        final var provider = type(request.getProvider());
        return toResponse(providers.get(provider).createDraft(request, userId),
                changeRequestId(provider, request.getProjectId(), request.getMrIid()));
    }

    @Override
    public CommentResponse updateDraft(final long draftId, final UpdateDraftRequest request, final String userId) {
        final var provider = type(request.getProvider());
        return toResponse(providers.get(provider).updateDraft(draftId, request, userId),
                changeRequestId(provider, request.getProjectId(), request.getMrIid()));
    }

    @Override
    public void deleteDraft(final VersionControlProviderType provider, final long draftId, final long projectId,
                            final long mrIid, final String userId) {
        providers.get(provider).deleteDraft(draftId, projectId, mrIid, userId);
    }

    @Override
    public List<CommentResponse> publishDrafts(final PublishReviewRequest request, final String userId) {
        final var provider = type(request.getProvider());
        providers.get(provider).publishDrafts(request, userId);
        return list(provider, request.getProjectId(), request.getMrIid(), userId);
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest request,
                                  final String userId) {
        providers.get(type(request.getProvider())).resolveDiscussion(discussionId, request, userId);
    }

    @Override
    public void applySuggestion(final VersionControlProviderType provider, final long suggestionId,
                                final String commitMessage, final String userId) {
        providers.get(provider).applySuggestion(suggestionId, commitMessage, userId);
    }

    private void reconcileDeleted(final VersionControlProviderType provider, final String mergeRequestId,
                                  final List<PublishedComment> published) {
        final var present = new HashSet<>(published.stream().map(PublishedComment::numericId).toList());
        final var now = System.currentTimeMillis();
        commentStore.list().stream()
                .filter(comment -> mergeRequestId.equals(comment.changeRequestId()))
                .filter(comment -> source(provider).equals(comment.source()) && !comment.deleted())
                .filter(comment -> !present.contains(comment.noteId()))
                .map(comment -> new CommentRecord(comment.id(), comment.provider(), comment.changeRequestId(),
                        comment.discussionId(), comment.noteId(), comment.authorId(), comment.authorUsername(), comment.body(),
                        comment.state(), comment.resolvable(), comment.resolved(), comment.individualNote(), true,
                        comment.source(), comment.createdAt(), now, now, comment.position(), comment.suggestions(),
                        comment.persona()))
                .forEach(syncService::synchronize);
    }

    private CommentRecord toRecord(final VersionControlProviderType provider, final PublishedComment comment,
                                   final long projectId, final long mrIid) {
        return new CommentRecord(CommentRecord.key(provider, Long.toString(projectId), mrIid,
                comment.numericId()), provider, changeRequestId(provider, projectId, mrIid),
                comment.threadId(), comment.numericId(), comment.authorId(), comment.authorUsername(), comment.body(),
                comment.state(), comment.resolvable(), comment.resolved(), comment.individualNote(), false, source(provider),
                comment.createdAt(), comment.updatedAt(), System.currentTimeMillis(), comment.position(),
                suggestions(comment), comment.persona());
    }

    private String changeRequestId(final VersionControlProviderType provider, final long projectId, final long mrIid) {
        return com.phonepe.arnold.core.model.vcs.ChangeRequestRecord.key(provider, Long.toString(projectId), mrIid);
    }

    private static VersionControlProviderType type(final CreateDiscussionRequest.VersionControlProvider provider) {
        return provider == null ? VersionControlProviderType.GITLAB : VersionControlProviderType.valueOf(provider.name());
    }

    private static String source(final VersionControlProviderType provider) {
        return provider.name().toLowerCase(java.util.Locale.ROOT);
    }

    private CommentResponse toResponse(final PublishedComment comment, final String mergeRequestId) {
        return toResponse(comment, mergeRequestId, null, null, null);
    }

    private CommentResponse toResponse(final PublishedComment comment, final String mergeRequestId,
                                       final ReviewFeedbackDocument feedback, final PersonaAttribution persona,
                                       final String personaName) {
        return new CommentResponse(comment.providerId(), mergeRequestId, comment.threadId(), comment.numericId(),
                comment.draftId(), comment.authorId(), comment.authorUsername(), comment.body(), comment.state(),
                comment.resolvable(), comment.resolved(), comment.individualNote(), false, GITLAB_SOURCE, comment.createdAt(),
                comment.updatedAt(), System.currentTimeMillis(), feedback == null ? null : feedback.getAcceptanceStatus(),
                feedback == null ? null : feedback.getAssessedSha(), feedback == null ? null : feedback.getReasoning(),
                comment.position(), suggestions(comment),
                runId(persona, feedback),
                persona == null ? null : persona.personaId(), persona == null ? null : persona.version(), personaName,
                reviewSource(persona, comment.authorUsername()));
    }

    private String runId(final PersonaAttribution persona, final ReviewFeedbackDocument feedback) {
        if (persona != null) {
            return persona.runId();
        }
        return feedback == null ? null : feedback.getAnalysisRunId();
    }

    private CommentResponse toResponse(final CommentRecord comment) {
        final var persona = comment.persona();
        return new CommentResponse(comment.id(), comment.changeRequestId(), comment.discussionId(), comment.noteId(),
                null, comment.authorId(), comment.authorUsername(), comment.body(), comment.state(), comment.resolvable(),
                comment.resolved(), comment.individualNote(), comment.deleted(), comment.source(), comment.createdAt(),
                comment.providerUpdatedAt(), comment.lastSyncedAt(), null, null, null, comment.position(),
                comment.suggestions() == null ? List.of() : comment.suggestions(),
                persona == null ? null : persona.runId(), persona == null ? null : persona.personaId(),
                persona == null ? null : persona.version(), null,
                reviewSource(persona, comment.authorUsername()));
    }

    /**
     * The note's persona marker wins; feedback ingested with {@code persona_id} covers notes posted without one.
     */
    private static PersonaAttribution attribution(final PersonaAttribution marker,
                                                  final ReviewFeedbackDocument feedback) {
        if (marker != null) {
            return marker;
        }
        if (feedback == null || feedback.getPersonaId() == null || feedback.getPersonaId().isBlank()) {
            return null;
        }
        return new PersonaAttribution(feedback.getAnalysisRunId(), feedback.getPersonaId(),
                feedback.getPersonaVersion() == null ? 0 : feedback.getPersonaVersion());
    }

    // Feedback documents are not an AI signal: comment assessment also ingests feedback for human comments.
    private String reviewSource(final PersonaAttribution persona, final String authorUsername) {
        if (persona != null) {
            return CommentResponse.SOURCE_PERSONA;
        }
        return aiAuthor.equals(authorUsername)
                ? CommentResponse.SOURCE_AI : CommentResponse.SOURCE_HUMAN;
    }

    /**
     * Persona names come from the run's snapshot so renamed or deleted personas still show the name used at review time.
     */
    private Map<String, String> personaNames(final java.util.Collection<PersonaAttribution> attributions) {
        final var names = new HashMap<String, String>();
        attributions.stream().map(PersonaAttribution::runId).filter(Objects::nonNull).distinct().forEach(runId -> {
            try {
                analysisRunStore.get(runId).ifPresent(run -> run.reviewPersonas().forEach(persona ->
                        names.put(personaKey(runId, persona.id()), persona.name())));
            } catch (RuntimeException error) {
                log.warn("Could not resolve persona names for runId={}", runId, error);
            }
        });
        return names;
    }

    private static String personaKey(final PersonaAttribution attribution) {
        return personaKey(attribution.runId(), attribution.personaId());
    }

    private static String personaKey(final String runId, final String personaId) {
        return runId + ":" + personaId;
    }

    private Map<Long, ReviewFeedbackDocument> feedback(
            final long projectId, final long mrIid, final List<PublishedComment> comments) {
        try {
            return reviewFeedbackService.feedbackByNote(projectId, mrIid,
                    comments.stream().map(PublishedComment::numericId).toList());
        } catch (Exception error) {
            // The search client rethrows checked IOExceptions undeclared, e.g. when Elasticsearch is not deployed.
            log.warn("Could not enrich discussions with review feedback for projectId={}, mrIid={}",
                    projectId, mrIid, error);
            return Map.of();
        }
    }

    private List<com.phonepe.arnold.models.comment.SuggestionResponse> suggestions(
            final PublishedComment comment) {
        return comment.suggestions() == null ? List.of() : comment.suggestions();
    }
}
