package com.phonepe.arnold.core.review;

import com.phonepe.arnold.core.services.impl.ProjectionQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.arnold.core.model.review.AnalysisOutcome;
import com.phonepe.arnold.core.model.review.ArnoldReview;
import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.AnalysisCompleted;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.ChangeClosed;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.ChangeMerged;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.ChangeReopened;
import com.phonepe.arnold.core.model.review.ReviewEventPayload.InvokeAnalysis;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.review.actions.AnalysisCompletedAction;
import com.phonepe.arnold.core.review.actions.ChangeClosedAction;
import com.phonepe.arnold.core.review.actions.ChangeReopenedAction;
import com.phonepe.arnold.core.review.actions.InvokeAnalysisAction;
import com.phonepe.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.arnold.core.review.guards.AnalysisRunMatchesGuard;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.impl.VersionControlSyncServiceImpl;
import com.phonepe.arnold.core.statemachine.ActionResolver;
import com.phonepe.arnold.core.statemachine.IllegalTransitionException;
import com.phonepe.arnold.core.statemachine.StateInfo;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.statemachine.TransitionEvent;
import com.phonepe.arnold.core.statemachine.TransitionResult;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.arnold.core.storage.memory.InMemoryStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the review transition table wired by {@link ReviewStateMachineFactory} against the design
 * diagram plus the three source-of-truth refinements: GitLab merge/close are global (fire from any
 * non-terminal state) and lenient (stale facts ignored), failed analysis still lands in ANALYSED, and
 * a closed MR can be reopened back to its pre-close state.
 */
class ReviewStateMachineFactoryTest {

    private static final String MR = "proj-1:mr-42";

    private InMemoryStateStore<ReviewState, ReviewStateData> store;
    private StubOrchestrator orchestrator;
    private StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> machine;

    @BeforeEach
    void setUp() {
        store = new InMemoryStateStore<>();
        orchestrator = new StubOrchestrator();
        machine = new ReviewStateMachineFactory(store, resolver()).create();
        machine.initialize(MR, StateInfo.of(ReviewState.CREATED, ReviewStateData.initial()));
    }

    private ReviewState state() {
        return machine.currentState(MR).orElseThrow().state();
    }

    private ReviewStateData data() {
        return machine.currentState(MR).orElseThrow().data();
    }

    private TransitionEvent<ReviewEvent, ReviewEventPayload> invoke(final String... personas) {
        return TransitionEvent.of(ReviewEvent.INVOKE_ANALYSIS,
                new InvokeAnalysis(List.of(personas), "author@phonepe"));
    }

    private String startAnalysis(final String... personas) {
        return machine.fire(MR, invoke(personas)).data().analysisRunId();
    }

    private void completeAnalysis(final String runId, final int personas) {
        machine.fire(MR, TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                new AnalysisCompleted(runId, personas, AnalysisOutcome.SUCCEEDED, null)));
    }

    @Nested
    class HappyPath {

        @Test
        void testFire_shouldReachMerged_whenAuthorDrivesFullHappyPath() {
            final var runId = startAnalysis("netra", "vidya");
            assertEquals(ReviewState.ANALYSING, state());
            assertEquals(2, data().personaCount());

            completeAnalysis(runId, 2);
            assertEquals(ReviewState.ANALYSED, state());
            assertEquals(AnalysisOutcome.SUCCEEDED, data().analysisOutcome());

            machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY));
            assertEquals(ReviewState.REVIEW_READY, state());

            machine.fire(MR, TransitionEvent.of(ReviewEvent.CHANGE_MERGED, new ChangeMerged("abc123")));
            assertEquals(ReviewState.MERGED, state());
        }
    }

    @Nested
    class AnalysisOutcomes {

        @Test
        void testFire_shouldLandInAnalysed_whenAnalysisFailed() {
            final var runId = startAnalysis("netra");

            // a failed run is still a COMPLETED event carrying a FAILED outcome — the machine treats it
            // as a normal arrival at ANALYSED; retry/alerting is handled outside.
            machine.fire(MR, TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                    new AnalysisCompleted(runId, 1, AnalysisOutcome.FAILED, "runner timeout")));

            assertEquals(ReviewState.ANALYSED, state());
            assertEquals(AnalysisOutcome.FAILED, data().analysisOutcome());
            assertEquals("runner timeout", data().outcomeDetail());
        }

        @Test
        void testFire_shouldLandInAnalysed_whenAnalysisPartial() {
            final var runId = startAnalysis("netra", "vidya");

            machine.fire(MR, TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                    new AnalysisCompleted(runId, 2, AnalysisOutcome.PARTIAL, "1/2 personas failed")));

            assertEquals(ReviewState.ANALYSED, state());
            assertEquals(AnalysisOutcome.PARTIAL, data().analysisOutcome());
        }
    }

    @Nested
    class ReRunBranches {

        @Test
        void testFire_shouldReturnToAnalysing_whenAuthorRerunsFromAnalysed() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);

            machine.fire(MR, invoke("netra", "vidya"));

            assertEquals(ReviewState.ANALYSING, state());
            assertEquals(2, orchestrator.startCount.get());
        }

        @Test
        void testFire_shouldReturnToAnalysing_whenReviewerRerunsFromReviewReady() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);
            machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY));

            machine.fire(MR, invoke("netra"));

            assertEquals(ReviewState.ANALYSING, state());
        }
    }

    @Nested
    class LegacyMergeReadiness {

        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        @Test
        void testDeserialize_shouldAllowReanalysis_whenStoredStateIsReadyForMerge() throws Exception {
            final var review = mapper.readValue("""
                    {"id":"legacy", "state":"READY_FOR_MERGE", "data":{"analysisRunId":"previous-run"}}
                    """, ArnoldReview.class);
            assertEquals(ReviewState.REVIEW_READY, review.state());
            assertEquals("previous-run", review.data().analysisRunId());
            assertEquals("REVIEW_READY", mapper.readTree(mapper.writeValueAsString(review)).get("state").asText());
            machine.initialize(review.id(), StateInfo.of(review.state(), review.data()));

            machine.fire(review.id(), invoke("netra"));

            assertEquals(ReviewState.ANALYSING, machine.currentState(review.id()).orElseThrow().state());
        }

        @Test
        void testReopen_shouldRestoreReviewReady_whenStoredPreCloseStateIsReadyForMerge() throws Exception {
            final var review = mapper.readValue("""
                    {"id":"legacy-closed", "state":"CLOSED", "data":{"preCloseState":"READY_FOR_MERGE"}}
                    """, ArnoldReview.class);
            assertEquals(ReviewState.REVIEW_READY, review.data().preCloseState());
            assertEquals("REVIEW_READY", mapper.readTree(mapper.writeValueAsString(review))
                    .get("data").get("preCloseState").asText());
            machine.initialize(review.id(), StateInfo.of(review.state(), review.data()));

            machine.tryFire(review.id(), TransitionEvent.of(ReviewEvent.CHANGE_REOPENED, new ChangeReopened("dev")));

            assertEquals(ReviewState.REVIEW_READY, machine.currentState(review.id()).orElseThrow().state());
            assertNull(machine.currentState(review.id()).orElseThrow().data().preCloseState());
        }
    }

    /** Point 2: GitLab is the source of truth — merge/close absorb from (almost) any state, leniently. */
    @Nested
    class SourceOfTruthSync {

        @Test
        void testSynchronize_shouldPreserveReviewState_whenGitLabMergeReadinessChanges() {
            final var changes = mock(ChangeRequestService.class);
            when(changes.synchronize(any(ChangeRequestRecord.class))).thenReturn(true);
            final var sync = new VersionControlSyncServiceImpl(changes, mock(AerospikeCommentStore.class),
                    new ProjectionQueue(mock(AerospikeProjectionEventStore.class)), machine);
            completeAnalysis(startAnalysis("netra"), 1);
            machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY));
            final var previousData = data();

            for (final var mergeStatus : List.of("mergeable", "not_approved")) {
                final var change = new ChangeRequestRecord(MR, VersionControlProviderType.GITLAB, "1", "2", 2,
                        "group/project", "Title", "Description", "https://gitlab.example/group/project/-/merge_requests/2",
                        "feature", "main", "sha", "opened", mergeStatus, false, 1L, List.of(), List.of(), List.of(), 2, 3);
                sync.synchronize(new ChangeRequestSync(change, ChangeRequestSync.Action.UPSERT, "dev"));

                assertEquals(ReviewState.REVIEW_READY, state());
                assertEquals(previousData, data());
            }
        }

        @Test
        void testTryFire_shouldClose_whenClosedWhileAnalysing() {
            startAnalysis("netra");

            final var result = machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            assertEquals(TransitionResult.Status.TRANSITIONED, result.status());
            assertEquals(ReviewState.CLOSED, state());
        }

        @Test
        void testTryFire_shouldClose_whenClosedWhileCreated() {
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));
            assertEquals(ReviewState.CLOSED, state());
        }

        @Test
        void testTryFire_shouldMerge_whenMergedDirectlyFromAnalysed() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);

            // merged upstream without going through REVIEW_READY — still authoritative
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_MERGED, new ChangeMerged("sha")));

            assertEquals(ReviewState.MERGED, state());
        }

        @Test
        void testTryFire_shouldIgnoreStaleCompletion_whenClosedDuringAnalysis() {
            final var runId = startAnalysis("netra");
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            // the in-flight analysis finishes after the close; the late callback must be a no-op
            final var late = machine.tryFire(MR, TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                    new AnalysisCompleted(runId, 1, AnalysisOutcome.SUCCEEDED, null)));

            assertEquals(TransitionResult.Status.IGNORED_NO_TRANSITION, late.status());
            assertEquals(ReviewState.CLOSED, state());
        }

        @Test
        void testTryFire_shouldIgnoreClose_whenAlreadyMerged() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_MERGED, new ChangeMerged("sha")));

            // a close arriving after a merge: MERGED is terminal, so it is ignored (not an error)
            final var result = machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            assertEquals(TransitionResult.Status.IGNORED_TERMINAL, result.status());
            assertEquals(ReviewState.MERGED, state());
        }

        @Test
        void testTryFire_shouldIgnoreDuplicateClose_whenAlreadyClosed() {
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            // CLOSED is already a target of the global close → duplicate fact is a no-op
            final var dup = machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            assertEquals(TransitionResult.Status.IGNORED_NO_TRANSITION, dup.status());
            assertEquals(ReviewState.CLOSED, state());
        }
    }

    /** Point 1: a closed MR can be reopened, restoring the state it held before the close. */
    @Nested
    class Reopen {

        @Test
        void testReopen_shouldRestoreReviewReady_whenClosedFromReviewReady() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);
            machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY));
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));
            assertEquals(ReviewState.CLOSED, state());

            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_REOPENED, new ChangeReopened("dev")));

            assertEquals(ReviewState.REVIEW_READY, state());
            assertNull(data().preCloseState());
        }

        @Test
        void testReopen_shouldRestoreCreated_whenClosedMidAnalysis() {
            startAnalysis("netra");
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));

            // analysis was abandoned on close, so reopen starts fresh from CREATED
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_REOPENED, new ChangeReopened("dev")));

            assertEquals(ReviewState.CREATED, state());
        }

        @Test
        void testReopen_shouldAllowReanalysis_afterRestoringToCreated() {
            startAnalysis("netra");
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_CLOSED, new ChangeClosed("ci")));
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_REOPENED, new ChangeReopened("dev")));

            machine.fire(MR, invoke("netra"));

            assertEquals(ReviewState.ANALYSING, state());
        }
    }

    @Nested
    class RunIdFencing {

        @Test
        void testTryFire_shouldIgnore_whenCompletionCarriesStaleRunId() {
            startAnalysis("netra");      // run-1 in flight

            final var result = machine.tryFire(MR, TransitionEvent.of(ReviewEvent.ANALYSIS_COMPLETED,
                    new AnalysisCompleted("run-stale", 1, AnalysisOutcome.SUCCEEDED, null)));

            assertEquals(TransitionResult.Status.IGNORED_GUARD_REJECTED, result.status());
            assertEquals(ReviewState.ANALYSING, state());
        }

        @Test
        void testFire_shouldAdvance_whenCompletionMatchesCurrentRun() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);
            assertEquals(ReviewState.ANALYSED, state());
        }
    }

    /** Strict fire() is for user commands and must reject invalid ones loudly (→ 4xx at the edge). */
    @Nested
    class StrictCommands {

        @Test
        void testFire_shouldThrow_whenMarkReadyFromCreated() {
            assertThrows(IllegalTransitionException.class,
                    () -> machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY)));
        }

        @Test
        void testFire_shouldThrow_whenReanalysisRequestedOnMergedTerminal() {
            final var runId = startAnalysis("netra");
            completeAnalysis(runId, 1);
            machine.fire(MR, TransitionEvent.of(ReviewEvent.MARK_REVIEW_READY));
            machine.tryFire(MR, TransitionEvent.of(ReviewEvent.CHANGE_MERGED, new ChangeMerged("sha")));

            assertThrows(IllegalTransitionException.class, () -> machine.fire(MR, invoke("netra")));
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private ActionResolver resolver() {
        final var bindings = Map.<Class<?>, Object>of(
                InvokeAnalysisAction.class, new InvokeAnalysisAction(orchestrator),
                AnalysisCompletedAction.class, new AnalysisCompletedAction(),
                ChangeClosedAction.class, new ChangeClosedAction(),
                ChangeReopenedAction.class, new ChangeReopenedAction(),
                AnalysisRunMatchesGuard.class, new AnalysisRunMatchesGuard());
        return new ActionResolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T resolve(final Class<T> type) {
                return (T) bindings.get(type);
            }
        };
    }

    /** Records analysis starts and mints deterministic run ids; does not drive callbacks itself. */
    private static final class StubOrchestrator implements AnalysisOrchestrator {
        private final AtomicInteger startCount = new AtomicInteger();

        @Override
        public String startAnalysis(final String mrId, final InvokeAnalysis request) {
            return "run-" + startCount.incrementAndGet();
        }

        @Override
        public void recordPersonaCompletion(final String mrId, final PersonaReviewResult result) {
            // not exercised by table tests
        }
    }
}
