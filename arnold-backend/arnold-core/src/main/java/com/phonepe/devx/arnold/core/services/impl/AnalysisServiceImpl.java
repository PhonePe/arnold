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
import com.phonepe.devx.arnold.core.services.PersonaService;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.TransitionEvent;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.models.analysis.AnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.AnalysisPersonaResponse;
import com.phonepe.devx.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.devx.arnold.models.analysis.StartAnalysisRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Singleton
public class AnalysisServiceImpl implements AnalysisService {
    private static final List<String> IMPROVE_PERSONAS = List.of("improve");

    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine;
    private final AnalysisOrchestrator orchestrator;
    private final AerospikeAnalysisRunStore runStore;
    private final PersonaService personaService;
    private final PipelineTriggerClient pipelines;

    @Inject
    public AnalysisServiceImpl(final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine,
                               final AnalysisOrchestrator orchestrator,
                               final AerospikeAnalysisRunStore runStore,
                               final PersonaService personaService,
                               final PipelineTriggerClient pipelines) {
        this.stateMachine = stateMachine;
        this.orchestrator = orchestrator;
        this.runStore = runStore;
        this.personaService = personaService;
        this.pipelines = pipelines;
    }

    @Override
    public AnalysisResponse start(final StartAnalysisRequest request, final String userId) {
        if (!IMPROVE_PERSONAS.equals(request.getPersonaIds())) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var reviewPersonas = personaService.resolveForAnalysis(userId, request.getReviewPersonaIds());
        final var fingerprint = Integer.toHexString(Objects.hash(request.getPersonaIds(), reviewPersonas,
                request.getRfcUrl(), request.getJiraTickets(), request.getReviewContext(), request.getExecution()));
        stateMachine.initialize(request.getMergeRequestId(), StateInfo.of(ReviewState.CREATED, ReviewStateData.initial()));
        final var state = stateMachine.fire(request.getMergeRequestId(), TransitionEvent.of(ReviewEvent.INVOKE_ANALYSIS,
                new ReviewEventPayload.InvokeAnalysis(request.getPersonaIds(), userId, request.getRfcUrl(),
                        request.getJiraTickets() == null ? List.of() : request.getJiraTickets(),
                        request.getReviewContext(), reviewPersonas, fingerprint, request.getExecution())));
        return response(run(state.data().analysisRunId()), state.state());
    }

    @Override
    public AnalysisResponse get(final String runId, final String userId) {
        final var run = run(runId);
        if (!Objects.equals(run.invokedBy(), userId)) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        return responseForRun(run);
    }

    @Override
    public List<AnalysisResponse> list(final String userId) {
        // ponytail: full scan is sufficient at current run volume; add a user index when history volume requires it.
        return runStore.list().stream()
                .filter(run -> Objects.equals(run.invokedBy(), userId))
                .sorted(Comparator.comparingLong(AnalysisRun::createdAt).reversed())
                .map(this::responseForRun)
                .toList();
    }

    @Override
    public AnalysisResponse completePersona(final PersonaCompletionRequest request) {
        final var outcome = request.getOutcome() == PersonaCompletionRequest.Outcome.SUCCEEDED
                ? PersonaReviewResult.Outcome.SUCCEEDED : PersonaReviewResult.Outcome.FAILED;
        orchestrator.recordPersonaCompletion(request.getMergeRequestId(), new PersonaReviewResult(request.getRunId(),
                request.getPersonaId(), outcome, request.getDetail(), request.getFindings()));
        return responseForRun(request.getRunId());
    }

    private AnalysisResponse responseForRun(final String runId) {
        return responseForRun(run(runId));
    }

    private AnalysisResponse responseForRun(final AnalysisRun run) {
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
        final var successes = run.results().values().stream().filter(PersonaReviewResult::succeeded).count();
        final var outcome = "failed".equals(run.status()) || !run.results().isEmpty() && successes == 0
                ? "FAILED"
                : "completed".equals(run.status())
                ? successes == run.personaIds().size() ? "SUCCEEDED" : "PARTIAL"
                : null;
        final var detail = run.failureDetail() != null ? run.failureDetail() : run.results().values().stream()
                .map(PersonaReviewResult::detail).filter(Objects::nonNull).findFirst().orElse(null);
        return new AnalysisResponse(run.mergeRequestId(), state.name(), run.id(), run.personaIds(), run.status(),
                run.results().size(), run.pipelineId(), pipelineUrl(run.pipelineId()), outcome, detail,
                run.reviewPersonas().stream()
                .map(persona -> new AnalysisPersonaResponse(persona.id(), persona.name(), persona.version())).toList(),
                run.results().values().stream().flatMap(result -> result.findings().stream()).toList(),
                run.createdAt(), run.updatedAt(), run.execution());
    }

    private String pipelineUrl(final Long pipelineId) {
        return pipelineId == null ? null : pipelines.pipelineUrl(pipelineId);
    }
}
