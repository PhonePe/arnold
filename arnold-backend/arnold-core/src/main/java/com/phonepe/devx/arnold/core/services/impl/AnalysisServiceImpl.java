package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.devx.arnold.core.services.AnalysisService;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.TransitionEvent;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.devx.arnold.models.analysis.StartAnalysisRequest;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AnalysisServiceImpl implements AnalysisService {
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine;
    private final AnalysisOrchestrator orchestrator;
    private final AerospikeAnalysisRunStore runStore;

    @Inject
    public AnalysisServiceImpl(final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine,
                               final AnalysisOrchestrator orchestrator,
                               final AerospikeAnalysisRunStore runStore) {
        this.stateMachine = stateMachine;
        this.orchestrator = orchestrator;
        this.runStore = runStore;
    }

    @Override
    public AnalysisResponse start(final StartAnalysisRequest request, final String userId) {
        stateMachine.initialize(request.getMergeRequestId(), StateInfo.of(ReviewState.CREATED, ReviewStateData.initial()));
        final var state = stateMachine.fire(request.getMergeRequestId(), TransitionEvent.of(ReviewEvent.INVOKE_ANALYSIS,
                new ReviewEventPayload.InvokeAnalysis(request.getPersonaIds(), userId)));
        return response(run(state.data().analysisRunId()), state.state());
    }

    @Override
    public AnalysisResponse get(final String runId, final String userId) {
        return responseForRun(runId);
    }

    @Override
    public AnalysisResponse completePersona(final PersonaCompletionRequest request) {
        final var outcome = request.getOutcome() == PersonaCompletionRequest.Outcome.SUCCEEDED
                ? PersonaReviewResult.Outcome.SUCCEEDED : PersonaReviewResult.Outcome.FAILED;
        orchestrator.recordPersonaCompletion(request.getMergeRequestId(), new PersonaReviewResult(request.getRunId(),
                request.getPersonaId(), outcome, request.getDetail()));
        return responseForRun(request.getRunId());
    }

    private AnalysisResponse responseForRun(final String runId) {
        final var run = run(runId);
        final var state = stateMachine.currentState(run.mergeRequestId())
                .map(StateInfo::state)
                .orElse(ReviewState.CREATED);
        return response(run, state);
    }

    private AnalysisRun run(final String runId) {
        return runStore.get(runId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    private AnalysisResponse response(final AnalysisRun run, final ReviewState state) {
        return new AnalysisResponse(run.mergeRequestId(), state.name(), run.id(), run.personaIds(), run.status(),
                run.results().size(), run.createdAt(), run.updatedAt());
    }
}
