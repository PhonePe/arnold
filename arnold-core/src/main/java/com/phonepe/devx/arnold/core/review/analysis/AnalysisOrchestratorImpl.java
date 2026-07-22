package com.phonepe.devx.arnold.core.review.analysis;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.review.AnalysisOutcome;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.services.AnalysisDispatcher;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.TransitionEvent;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;

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

    @Inject
    public AnalysisOrchestratorImpl(final AerospikeAnalysisRunStore runStore,
                                    final AnalysisDispatcher dispatcher,
                                    final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine) {
        this.runStore = runStore;
        this.dispatcher = dispatcher;
        this.stateMachine = stateMachine;
    }

    @Override
    public String startAnalysis(final String mrId, final ReviewEventPayload.InvokeAnalysis request) {
        final var existing = runStore.list().stream()
                .filter(run -> mrId.equals(run.mergeRequestId()))
                .filter(run -> "running".equals(run.status()))
                .filter(run -> run.personaIds().equals(request.personaIds()))
                .filter(run -> java.util.Objects.equals(run.invokedBy(), request.invokedBy()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().id();
        }
        final var now = System.currentTimeMillis();
        final var run = new AnalysisRun(UUID.randomUUID().toString(), mrId, request.personaIds(), request.invokedBy(),
                "running", java.util.Map.of(), now, now);
        runStore.create(run);
        request.personaIds().forEach(personaId -> dispatcher.dispatch(mrId, run.id(), personaId));
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
                return;
            }
            final var results = new HashMap<>(run.results());
            results.put(result.personaId(), result);
            final var completed = results.size() == run.personaIds().size();
            final var next = new AnalysisRun(run.id(), run.mergeRequestId(), run.personaIds(), run.invokedBy(),
                    completed ? "completed" : "running", results, run.createdAt(), System.currentTimeMillis());
            try {
                runStore.checkAndUpdate(next, current.version());
                if (completed) {
                    completeStateMachine(next);
                }
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Retry the persona join against the latest generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
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
