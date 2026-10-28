package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.devx.arnold.core.model.review.ArnoldReview;
import com.phonepe.devx.arnold.core.services.ProjectionService;
import com.phonepe.devx.arnold.core.storage.ReviewStateStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import com.phonepe.devx.arnold.models.es.AnalysisFindingDocument;
import com.phonepe.devx.arnold.models.es.AnalysisRunDocument;
import com.phonepe.devx.arnold.models.es.CommentDocument;
import com.phonepe.devx.arnold.models.es.FlowJobModelUsageDocument;
import com.phonepe.devx.arnold.models.es.FlowJobUsageDocument;
import com.phonepe.devx.arnold.models.es.MergeRequestDocument;
import com.phonepe.devx.arnold.models.es.ReviewEngagementDocument;
import com.phonepe.devx.arnold.models.es.SmartDiffDocument;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Singleton
public class ProjectionServiceImpl implements ProjectionService {
    private static final int MAX_ATTEMPTS = 10;
    private final AerospikeProjectionEventStore eventStore;
    private final AerospikeChangeRequestStore mergeRequestStore;
    private final AerospikeCommentStore commentStore;
    private final ReviewStateStore reviewStateStore;
    private final AerospikeAnalysisRunStore analysisRunStore;
    private final AerospikeSmartDiffStore smartDiffStore;
    private final AerospikeReviewProgressStore reviewProgressStore;
    private final AerospikeFlowJobUsageStore flowJobUsageStore;
    private final ESStorageManager esStorageManager;

    @Inject
    public ProjectionServiceImpl(final AerospikeProjectionEventStore eventStore,
                                 final AerospikeChangeRequestStore mergeRequestStore,
                                 final AerospikeCommentStore commentStore,
                                 final ReviewStateStore reviewStateStore,
                                 final AerospikeAnalysisRunStore analysisRunStore,
                                 final AerospikeSmartDiffStore smartDiffStore,
                                 final AerospikeReviewProgressStore reviewProgressStore,
                                 final AerospikeFlowJobUsageStore flowJobUsageStore,
                                 final ESStorageManager esStorageManager) {
        this.eventStore = eventStore;
        this.mergeRequestStore = mergeRequestStore;
        this.commentStore = commentStore;
        this.reviewStateStore = reviewStateStore;
        this.analysisRunStore = analysisRunStore;
        this.smartDiffStore = smartDiffStore;
        this.reviewProgressStore = reviewProgressStore;
        this.flowJobUsageStore = flowJobUsageStore;
        this.esStorageManager = esStorageManager;
    }

    @Override
    public int processPending(final int limit) {
        return eventStore.getByRefId("status:pending").stream()
                .filter(event -> "pending".equals(event.status()))
                .filter(event -> event.attempts() < MAX_ATTEMPTS)
                .sorted(Comparator.comparingLong(ProjectionEvent::createdAt))
                .limit(limit)
                .mapToInt(this::process)
                .sum();
    }

    private int process(final ProjectionEvent event) {
        try {
            switch (event.aggregateType()) {
                case ProjectionQueue.MERGE_REQUEST -> projectMergeRequest(event.aggregateId());
                case ProjectionQueue.COMMENT -> projectComment(event.aggregateId());
                case ProjectionQueue.ANALYSIS_RUN -> projectAnalysisRun(event.aggregateId());
                case ProjectionQueue.SMART_DIFF -> projectSmartDiff(event.aggregateId());
                case ProjectionQueue.REVIEW_PROGRESS -> projectReviewProgress(event.aggregateId());
                case ProjectionQueue.FLOW_JOB_USAGE -> projectFlowJobUsage(event.aggregateId());
                default -> throw new IllegalArgumentException("Unknown aggregate type: " + event.aggregateType());
            }
            eventStore.delete(event.id());
            return 1;
        } catch (RuntimeException failure) {
            log.error("Failed to project event id={}", event.id(), failure);
            if (event.attempts() + 1 >= MAX_ATTEMPTS) {
                eventStore.delete(event.id());
            } else {
                eventStore.update(new ProjectionEvent(event.id(), event.aggregateType(), event.aggregateId(),
                        event.operation(), "pending", event.attempts() + 1, event.createdAt(), 0));
            }
            return 0;
        }
    }

    private void projectMergeRequest(final String id) {
        final var mr = mergeRequestStore.get(id).orElseThrow();
        final ArnoldReview review = reviewStateStore.get(List.of(id)).get(id);
        final var data = review == null ? null : review.data();
        esStorageManager.save(SearchIndexReferences.CHANGE_REQUESTS, id, new MergeRequestDocument(mr.id(),
                mr.provider().name(), mr.repositoryId(), mr.providerId(), mr.number(), mr.repositoryPath(),
                mr.title(), mr.description(), mr.webUrl(), mr.state(), mr.mergeStatus(), mr.draft(), mr.authorId(),
                mr.assigneeIds(), mr.reviewerIds(), mr.labels(), mr.createdAt(), mr.mergedAt(), mr.closedAt(),
                mr.mergedAt() > 0 && mr.createdAt() > 0 ? Math.max(0, mr.mergedAt() - mr.createdAt()) : 0,
                mr.hasConflicts(), mr.blockingDiscussionsResolved(), mr.providerUpdatedAt(), mr.lastSyncedAt(),
                mr.projectArchived(), mr.sourceBranch(), mr.targetBranch(), mr.headSha(), mr.authorName(),
                mr.reviewerNames(),
                review == null || review.state() == null ? null : review.state().name(),
                data != null && data.analysisRunId() != null,
                data == null ? null : data.analysisRunId(),
                data == null || data.analysisOutcome() == null ? null : data.analysisOutcome().name()));
    }

    private void projectComment(final String id) {
        final var comment = commentStore.get(id).orElseThrow();
        final var mrAuthorId = comment.changeRequestId() == null ? null
                : mergeRequestStore.get(comment.changeRequestId()).map(mr -> mr.authorId()).orElse(null);
        esStorageManager.save(SearchIndexReferences.COMMENTS, id, new CommentDocument(comment.id(),
                comment.changeRequestId(), comment.discussionId(), comment.noteId(), comment.authorId(),
                comment.body(), comment.resolved(), comment.deleted(), comment.source(),
                comment.providerUpdatedAt(), comment.createdAt(), comment.authorUsername(), comment.state(),
                comment.resolvable(), comment.individualNote(), mrAuthorId,
                mrAuthorId != null && mrAuthorId.equals(comment.authorId()),
                comment.persona() == null ? null : comment.persona().runId(),
                comment.persona() == null ? null : comment.persona().personaId(),
                comment.persona() == null ? null : comment.persona().version()));
    }

    private void projectAnalysisRun(final String id) {
        final var run = analysisRunStore.get(id).orElseThrow();
        final var mr = MergeRequestKey.parse(run.mergeRequestId());
        final var results = run.results() == null ? Map.<String, PersonaReviewResult>of() : run.results();
        final var succeeded = (int) results.values().stream().filter(PersonaReviewResult::succeeded).count();
        final var failed = results.size() - succeeded;
        final var findings = results.values().stream().flatMap(result -> result.findings().stream()).toList();
        final var terminal = !"running".equals(run.status());
        final var usage = TokenUsage.merge(results.values().stream().map(PersonaReviewResult::usage).toList());
        esStorageManager.save(SearchIndexReferences.ANALYSIS_RUNS, id, new AnalysisRunDocument(run.id(),
                AnalysisRunDocument.TYPE, run.mergeRequestId(), mr.provider(), mr.repositoryId(), mr.number(),
                run.status(), terminal ? outcome(run, succeeded) : null, run.invokedBy(), run.personaIds(),
                run.reviewPersonas().stream().map(persona -> persona.id() + "@" + persona.version()).toList(),
                run.reviewPersonas().stream().map(ReviewPersonaSnapshot::id).toList(),
                run.personaIds().size(), succeeded, failed, findings.size(),
                (int) findings.stream().filter(finding -> "posted".equals(finding.publicationStatus())).count(),
                (int) findings.stream().filter(finding -> "failed".equals(finding.publicationStatus())).count(),
                run.execution() == null ? null : run.execution().harness(),
                run.execution() == null ? null : run.execution().model(),
                run.pipelineId(), run.failureDetail(), run.createdAt(), run.updatedAt(),
                terminal ? Math.max(0, run.updatedAt() - run.createdAt()) : 0,
                usage == null ? null : usage.inputTokens(), usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.inputTokens() + usage.outputTokens(),
                usage == null ? null : usage.cacheReadTokens()));
        results.values().forEach(result -> result.findings().forEach(finding -> {
            final var findingId = AnalysisFindingDocument.id(run.id(), finding.id());
            esStorageManager.save(SearchIndexReferences.ANALYSIS_RUNS, findingId, new AnalysisFindingDocument(
                    findingId, AnalysisFindingDocument.TYPE, run.id(), run.mergeRequestId(), mr.provider(),
                    mr.repositoryId(), mr.number(), run.invokedBy(), finding.personaId(), finding.personaVersion(),
                    finding.publicationStatus(), finding.filePath(), run.createdAt()));
        }));
    }

    private static String outcome(final AnalysisRun run, final int succeeded) {
        if ("failed".equals(run.status()) || succeeded == 0) {
            return "FAILED";
        }
        return succeeded >= run.personaIds().size() ? "SUCCEEDED" : "PARTIAL";
    }

    private void projectSmartDiff(final String id) {
        final var diff = smartDiffStore.get(id).orElseThrow();
        // smart-diff:<provider>:<repositoryId>:<number>:<headSha>
        final var mr = MergeRequestKey.parse(id.substring("smart-diff:".length(), id.lastIndexOf(':')));
        final var terminal = "completed".equals(diff.status()) || "failed".equals(diff.status());
        esStorageManager.save(SearchIndexReferences.ANALYSIS_RUNS, id, new SmartDiffDocument(diff.id(),
                SmartDiffDocument.TYPE, mr.id(), mr.provider(), mr.repositoryId(), mr.number(), diff.headSha(),
                diff.status(), diff.requestedBy(), diff.groups() == null ? 0 : diff.groups().size(),
                diff.commentAssessmentStatus(), diff.assessedCommentCount(),
                diff.execution() == null ? null : diff.execution().harness(),
                diff.execution() == null ? null : diff.execution().model(),
                diff.errorMessage(), diff.requestedAt(), diff.updatedAt(),
                terminal && diff.requestedAt() > 0 ? Math.max(0, diff.updatedAt() - diff.requestedAt()) : 0,
                diff.usage() == null ? null : diff.usage().inputTokens(),
                diff.usage() == null ? null : diff.usage().outputTokens(),
                diff.usage() == null ? null : diff.usage().inputTokens() + diff.usage().outputTokens(),
                diff.usage() == null ? null : diff.usage().cacheReadTokens()));
    }

    private void projectFlowJobUsage(final String id) {
        final var job = flowJobUsageStore.get(id).orElseThrow();
        final var usage = job.usage();
        final var docId = "usage:" + job.id();
        esStorageManager.save(SearchIndexReferences.ANALYSIS_RUNS, docId, new FlowJobUsageDocument(docId,
                FlowJobUsageDocument.TYPE, job.flow(), job.runId(), job.target(), job.invokedBy(), job.harness(),
                job.status(), usage.jobUrl(), usage.models().stream().map(TokenUsage.Model::model).toList(),
                usage.inputTokens(), usage.outputTokens(), usage.inputTokens() + usage.outputTokens(),
                usage.cacheReadTokens(), job.createdAt(), job.updatedAt()));
        usage.models().forEach(model -> {
            final var modelDocId = docId + ":" + model.model();
            esStorageManager.save(SearchIndexReferences.ANALYSIS_RUNS, modelDocId, new FlowJobModelUsageDocument(
                    modelDocId, FlowJobModelUsageDocument.TYPE, job.id(), job.flow(), job.runId(), job.invokedBy(),
                    job.harness(), model.model(), model.inputTokens(), model.outputTokens(),
                    model.inputTokens() + model.outputTokens(), model.cacheReadTokens(), job.createdAt(),
                    job.updatedAt()));
        });
    }

    private void projectReviewProgress(final String id) {
        final var progress = reviewProgressStore.get(id).orElseThrow();
        // progress:<userId>:<provider>:<repositoryId>:<number>:<headSha>
        final var withoutSha = id.substring(0, id.lastIndexOf(':'));
        final var numberStart = withoutSha.lastIndexOf(':');
        final var repoStart = withoutSha.lastIndexOf(':', numberStart - 1);
        final var providerStart = withoutSha.lastIndexOf(':', repoStart - 1);
        final var userId = withoutSha.substring("progress:".length(), providerStart);
        final var mr = MergeRequestKey.parse(withoutSha.substring(providerStart + 1));
        final var feedback = progress.focusAreaFeedback() == null ? Map.<String, String>of()
                : progress.focusAreaFeedback();
        final var useful = (int) feedback.values().stream().filter("useful"::equals).count();
        final var notUseful = (int) feedback.values().stream().filter("not-useful"::equals).count();
        esStorageManager.save(SearchIndexReferences.REVIEW_ENGAGEMENT, id, new ReviewEngagementDocument(id,
                userId, mr.id(), mr.provider(), mr.repositoryId(), mr.number(), progress.headSha(),
                progress.filePaths() == null ? 0 : progress.filePaths().size(),
                progress.storyStepIds() == null ? 0 : progress.storyStepIds().size(),
                feedback.size(), useful, notUseful, progress.updatedAt()));
    }

    record MergeRequestKey(String id, String provider, String repositoryId, long number) {
        static MergeRequestKey parse(final String id) {
            final var parts = Objects.requireNonNull(id, "mergeRequestId").split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Unexpected merge request id: " + id);
            }
            return new MergeRequestKey(id, parts[0], parts[1], Long.parseLong(parts[2]));
        }
    }
}
