package com.phonepe.devx.arnold.core.review.analysis;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.model.events.FlowJobUsageReported;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.review.AnalysisOutcome;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.services.AnalysisDispatcher;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.TransitionEvent;
import com.phonepe.devx.arnold.core.services.impl.ProjectionQueue;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.models.analysis.AnalysisFindingResponse;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class AnalysisOrchestratorImpl implements AnalysisOrchestrator {
    private static final int MAX_UPDATE_ATTEMPTS = 8;
    /** Longer than the slowest Clio job timeout (2h45m), so only runs that will never report back expire. */
    static final long EXECUTION_TIMEOUT = 3 * 60 * 60 * 1000L;

    private static final java.util.Set<String> FAILED_PIPELINE_STATUSES = java.util.Set.of("failed", "canceled", "skipped");

    private final AerospikeAnalysisRunStore runStore;
    private final AnalysisDispatcher dispatcher;
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine;
    private final ArnoldSignalPublisher signals;
    private final ProjectionQueue projections;

    @Inject
    public AnalysisOrchestratorImpl(final AerospikeAnalysisRunStore runStore,
                                    final AnalysisDispatcher dispatcher,
                                    final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine,
                                    final ArnoldSignalPublisher signals,
                                    final ProjectionQueue projections) {
        this.runStore = runStore;
        this.dispatcher = dispatcher;
        this.stateMachine = stateMachine;
        this.signals = signals;
        this.projections = projections;
    }

    @Override
    public String startAnalysis(final String mrId, final ReviewEventPayload.InvokeAnalysis request) {
        final var running = runStore.list().stream()
                .filter(run -> mrId.equals(run.mergeRequestId()))
                .filter(run -> "running".equals(run.status()))
                .toList();
        running.stream().filter(this::timedOut).forEach(run ->
                updateDispatch(run.id(), run.pipelineId(), "Timed out waiting for Clio to report results"));
        final var existing = running.stream()
                .filter(run -> !timedOut(run))
                .filter(run -> run.personaIds().equals(request.personaIds()))
                .filter(run -> java.util.Objects.equals(run.invokedBy(), request.invokedBy()))
                .filter(run -> java.util.Objects.equals(run.requestFingerprint(), request.requestFingerprint()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().id();
        }
        final var now = System.currentTimeMillis();
        final var run = new AnalysisRun(UUID.randomUUID().toString(), mrId, request.personaIds(), request.invokedBy(),
                "running", java.util.Map.of(), null, null, request.reviewPersonas(), request.requestFingerprint(), now,
                now, request.execution());
        runStore.create(run);
        project(run);
        try {
            final var pipelineId = dispatcher.dispatch(mrId, run.id(), request.personaIds().get(0), request.rfcUrl(),
                    request.jiraTickets(), request.reviewContext(), request.reviewPersonas());
            updateDispatch(run.id(), pipelineId, null);
        } catch (RuntimeException error) {
            updateDispatch(run.id(), null, "Could not trigger Clio Improve");
            throw error;
        }
        return run.id();
    }

    @Override
    public void recordPersonaCompletion(final String mrId, final PersonaReviewResult result) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = runStore.getWithVersion(result.runId())
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            final var run = current.value();
            if (!mrId.equals(run.mergeRequestId()) || !run.personaIds().contains(result.personaId())) {
                throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
            }
            if (run.results().containsKey(result.personaId())) {
                if ("completed".equals(run.status())) {
                    notifyFinished(run);
                }
                return;
            }
            final var results = new HashMap<>(run.results());
            results.put(result.personaId(), attributed(run, result));
            final var completed = results.size() == run.personaIds().size();
            final var next = new AnalysisRun(run.id(), run.mergeRequestId(), run.personaIds(), run.invokedBy(),
                    completed ? "completed" : "running", results, run.pipelineId(), run.failureDetail(),
                    run.reviewPersonas(), run.requestFingerprint(), run.createdAt(), System.currentTimeMillis(), run.execution());
            try {
                runStore.checkAndUpdate(next, current.version());
                project(next);
                if (result.usage() != null) {
                    signals.publish(FlowJobUsageReported.of("improve", run.id(), run.id(), run.mergeRequestId(),
                            run.invokedBy(), run.execution(), result.outcome().name().toLowerCase(), result.usage(),
                            run.createdAt()));
                }
                if (completed) {
                    completeStateMachine(next);
                    notifyFinished(next);
                }
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Retry the persona join against the latest generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    @Override
    public boolean isAbandoned(final String runId) {
        return runStore.get(runId).map(run -> !"running".equals(run.status()) || timedOut(run)).orElse(true);
    }

    @Override
    public void onPipelineFinished(final long pipelineId, final String pipelineStatus) {
        if (!FAILED_PIPELINE_STATUSES.contains(pipelineStatus)) {
            return;
        }
        runStore.list().stream()
                .filter(run -> "running".equals(run.status()))
                .filter(run -> run.pipelineId() != null && run.pipelineId() == pipelineId)
                .forEach(run -> failRun(run.id(), "Clio pipeline " + pipelineStatus));
    }

    private void failRun(final String runId, final String failureDetail) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = runStore.getWithVersion(runId).orElse(null);
            if (current == null || !"running".equals(current.value().status())) {
                return;
            }
            final var run = current.value();
            final var next = new AnalysisRun(run.id(), run.mergeRequestId(), run.personaIds(), run.invokedBy(),
                    "failed", run.results(), run.pipelineId(), failureDetail, run.reviewPersonas(),
                    run.requestFingerprint(), run.createdAt(), System.currentTimeMillis(), run.execution());
            try {
                runStore.checkAndUpdate(next, current.version());
                project(next);
                completeStateMachine(next);
                notifyFinished(next);
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest run generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private boolean timedOut(final AnalysisRun run) {
        return System.currentTimeMillis() - run.updatedAt() >= EXECUTION_TIMEOUT;
    }

    /**
     * Keeps only persona attributions that match the run's persona snapshot; the snapshot's version is authoritative.
     * Unknown personas fall back to the general (unattributed) bucket rather than rejecting the callback.
     */
    static PersonaReviewResult attributed(final AnalysisRun run, final PersonaReviewResult result) {
        final var versions = run.reviewPersonas().stream()
                .collect(Collectors.toMap(ReviewPersonaSnapshot::id, ReviewPersonaSnapshot::version, (a, b) -> a));
        final var findings = result.findings().stream().map(finding -> {
            if (finding.personaId() == null) {
                return finding;
            }
            final var version = versions.get(finding.personaId());
            if (version == null) {
                log.warn("Dropping unknown persona attribution personaId={} runId={}", finding.personaId(), run.id());
            }
            return new AnalysisFindingResponse(finding.id(), finding.headSha(), finding.filePath(), finding.oldPath(),
                    finding.newLine(), finding.body(), finding.publicationStatus(), finding.commentUrl(),
                    version == null ? null : finding.personaId(), version);
        }).toList();
        return new PersonaReviewResult(result.runId(), result.personaId(), result.outcome(), result.detail(), findings,
                result.usage());
    }

    private void notifyFinished(final AnalysisRun run) {
        final var successes = run.results().values().stream().filter(PersonaReviewResult::succeeded).count();
        final var title = successes == run.personaIds().size() ? "Analysis completed"
                : successes == 0 ? "Analysis failed" : "Analysis partially completed";
        final var findings = run.results().values().stream()
                .flatMap(result -> result.findings().stream()).toList();
        final var posted = findings.stream().filter(finding -> "posted".equals(finding.publicationStatus())).count();
        final var failed = findings.stream().filter(finding -> "failed".equals(finding.publicationStatus())).count();
        final var body = new StringBuilder(run.mergeRequestId());
        if (posted > 0) {
            body.append("\n").append(posted).append(" comment").append(posted == 1 ? "" : "s").append(" posted");
        }
        if (failed > 0) {
            body.append("\n").append(failed).append(" comment").append(failed == 1 ? "" : "s")
                    .append(" failed to post");
        }
        signals.publish(new NotificationRequested("analysis_finished:" + run.id(), NotificationType.ANALYSIS_FINISHED,
                "analysis", run.id(), java.util.Set.of(run.invokedBy()), title, body.toString(),
                reviewDeepLink(run), System.currentTimeMillis()));
    }

    private String reviewDeepLink(final AnalysisRun run) {
        // mergeRequestId is "provider:repositoryId:number"; build the canonical MR review link.
        final var parts = run.mergeRequestId().split(":", 3);
        if (parts.length != 3) return "/history?runId=" + run.id();
        return "/review/" + parts[0].toUpperCase() + "/" + parts[1] + "/" + parts[2] + "/runs/" + run.id();
    }

    private void updateDispatch(final String runId, final Long pipelineId, final String failureDetail) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = runStore.getWithVersion(runId)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            final var run = current.value();
            final var next = new AnalysisRun(run.id(), run.mergeRequestId(), run.personaIds(), run.invokedBy(),
                    failureDetail == null ? run.status() : "failed", run.results(), pipelineId, failureDetail,
                    run.reviewPersonas(), run.requestFingerprint(), run.createdAt(), System.currentTimeMillis(), run.execution());
            try {
                runStore.checkAndUpdate(next, current.version());
                project(next);
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest run generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private void project(final AnalysisRun run) {
        projections.enqueueBestEffort(ProjectionQueue.ANALYSIS_RUN, run.id(), run.updatedAt());
    }

    private void completeStateMachine(final AnalysisRun run) {
        final var successes = run.results().values().stream().filter(PersonaReviewResult::succeeded).count();
        final var outcome = successes == run.personaIds().size()
                ? AnalysisOutcome.SUCCEEDED
                : successes == 0 ? AnalysisOutcome.FAILED : AnalysisOutcome.PARTIAL;
        stateMachine.tryFire(run.mergeRequestId(), TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                new ReviewEventPayload.AnalysisCompleted(run.id(), run.personaIds().size(), outcome, null)));
    }
}
