package com.phonepe.arnold.core.review.analysis;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.review.AnalysisOutcome;
import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.services.AnalysisDispatcher;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.statemachine.TransitionEvent;
import com.phonepe.arnold.core.services.impl.ProjectionQueue;
import com.phonepe.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.UUID;

@Singleton
public class AnalysisOrchestratorImpl implements AnalysisOrchestrator {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

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
        final var existing = runStore.list().stream()
                .filter(run -> mrId.equals(run.mergeRequestId()))
                .filter(run -> "running".equals(run.status()))
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
            updateDispatch(run.id(), null, "Could not start the Improve runner job");
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
            results.put(result.personaId(), result);
            final var completed = results.size() == run.personaIds().size();
            final var next = new AnalysisRun(run.id(), run.mergeRequestId(), run.personaIds(), run.invokedBy(),
                    completed ? "completed" : "running", results, run.pipelineId(), run.failureDetail(),
                    run.reviewPersonas(), run.requestFingerprint(), run.createdAt(), System.currentTimeMillis(), run.execution());
            try {
                runStore.checkAndUpdate(next, current.version());
                project(next);
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
