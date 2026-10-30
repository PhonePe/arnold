package com.phonepe.devx.arnold.core.services;

import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.devx.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse.Member;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse.StartRequest;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GroupAnalysisServiceCoverageTest {
    private static final String GROUP = "group";
    private static final String MR_1 = "gitlab:42:1";
    private static final String MR_2 = "gitlab:42:2";

    private AerospikeGroupAnalysisRunStore store;
    private AerospikeMergeRequestGroupStore groups;
    private ChangeRequestService changeRequests;
    private PersonaService personas;
    private PipelineTriggerClient pipelines;
    private LeaderElectionService locks;
    private ArnoldSignalPublisher signals;
    private GroupAnalysisService service;
    private AtomicReference<GroupAnalysisRun> saved;

    @BeforeEach
    void setUp() {
        store = mock(AerospikeGroupAnalysisRunStore.class);
        groups = mock(AerospikeMergeRequestGroupStore.class);
        changeRequests = mock(ChangeRequestService.class);
        personas = mock(PersonaService.class);
        pipelines = mock(PipelineTriggerClient.class);
        locks = mock(LeaderElectionService.class);
        signals = mock(ArnoldSignalPublisher.class);
        service = new GroupAnalysisService(store, groups, changeRequests, personas, pipelines, locks, signals);
        saved = new AtomicReference<>();
        when(store.getWithVersion(anyString())).thenAnswer(call ->
                Optional.ofNullable(saved.get()).map(run -> Versioned.of(run, 1L)));
        when(store.get(anyString())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        doAnswer(call -> {
            saved.set(call.getArgument(0));
            return null;
        }).when(store).checkAndUpdate(any(GroupAnalysisRun.class), anyLong());
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong()))
                .thenReturn(new LeaderElectionService.Leadership(true, "me", Long.MAX_VALUE));
        when(pipelines.triggerPipeline(any())).thenReturn(77L);
        when(pipelines.pipelineUrl(77L)).thenReturn("https://pipeline/77");
    }

    private static Member member(final String id, final String head) {
        return new Member(id, 42, Long.parseLong(id.substring(id.lastIndexOf(':') + 1)), "team/api", "API", "",
                "https://gitlab.example/team/api/-/merge_requests/" + id, "feature", "main", "base", "start", head);
    }

    private static GroupAnalysisRun run(final String status, final long startedAt) {
        final var response = new GroupAnalysisResponse("run", GROUP, "Feature", "execution", status,
                List.of(member(MR_1, "head"), member(MR_2, "head")), Map.of(), List.of(), List.of(), null, null, null,
                false, 10, 10);
        return new GroupAnalysisRun("run", "owner", new StartRequest(null, null, null, null, null),
                List.of(new GroupAnalysisResponse.Persona("p", "P", "i", 1)), response, null, 0, false, startedAt,
                Map.of(), false, Map.of());
    }

    private void stubLiveMembers() {
        when(groups.get(GROUP)).thenReturn(Optional.of(new MergeRequestGroup(GROUP, "owner", "Feature", List.of(MR_1, MR_2), 1, 1)));
        for (final var id : List.of(MR_1, MR_2)) {
            final var stored = mock(ChangeRequestRecord.class);
            when(stored.provider()).thenReturn(VersionControlProviderType.GITLAB);
            when(stored.webUrl()).thenReturn("url-" + id);
            when(changeRequests.record(id)).thenReturn(Optional.of(stored));
            final var mr = mock(MergeRequestResponse.class);
            when(mr.id()).thenReturn(id);
            when(mr.state()).thenReturn("opened");
            when(mr.repositoryId()).thenReturn("42");
            when(mr.number()).thenReturn(Long.parseLong(id.substring(id.lastIndexOf(':') + 1)));
            when(mr.headSha()).thenReturn("head");
            when(mr.repositoryPath()).thenReturn("team/api");
            when(mr.title()).thenReturn("API");
            when(mr.description()).thenReturn("");
            when(mr.webUrl()).thenReturn("https://gitlab.example/team/api/-/merge_requests/" + id);
            when(mr.sourceBranch()).thenReturn("feature");
            when(mr.targetBranch()).thenReturn("main");
            when(changeRequests.resolve(VersionControlProviderType.GITLAB, "url-" + id, "owner")).thenReturn(mr);
            when(changeRequests.listDiffVersions(42L, mr.number(), "owner")).thenReturn(List.of(
                    new MergeRequestDiffVersionResponse(1, "old", "old", "old", 1, "t"),
                    new MergeRequestDiffVersionResponse(2, "base", "start", "head", 2, "t")));
        }
    }

    private static ArnoldErrorCode code(final Runnable action) {
        return assertThrows(ArnoldError.class, action::run).getErrorCode();
    }

    @Test
    void testList_shouldFilterSortAndLimit_whenRunsStored() {
        final var older = run("completed", System.currentTimeMillis());
        final var other = new GroupAnalysisRun("other", "owner", older.context(), List.of(),
                older.result().withGroupId("elsewhere"), null, 0, false, 0, Map.of(), false, Map.of());
        when(store.list()).thenReturn(List.of(older, other));
        when(groups.get(anyString())).thenReturn(Optional.empty());

        final var result = service.list(GROUP, 100L, 0);

        assertEquals(1, result.size());
        assertTrue(result.get(0).stale());
        assertTrue(service.list(GROUP, 5L, 10).isEmpty());
        assertEquals(2, service.list(null, null, 1000).size());
    }

    @Test
    void testManifest_shouldReturnOnlyForActiveMatchingExecution() {
        saved.set(run("queued", System.currentTimeMillis()));
        final var manifest = service.manifest("run", "execution");
        assertEquals(1, manifest.reviewPersonas().size());
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.manifest("run", "other")));
        saved.set(run("queued", 0));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.manifest("run", "execution")));
    }

    @Test
    void testGet_shouldMarkTimedOutRunFailed() {
        saved.set(run("running", 0));
        when(groups.get(GROUP)).thenReturn(Optional.empty());
        final var result = service.get("run");
        assertEquals("failed", result.runStatus());
        assertTrue(result.stale());
    }

    @Test
    void testGet_shouldReportUnverifiable_whenLiveCheckFails() {
        saved.set(run("completed", System.currentTimeMillis()));
        when(groups.get(GROUP)).thenReturn(Optional.of(new MergeRequestGroup(GROUP, "owner", "F", List.of(MR_1, MR_2), 1, 1)))
                .thenReturn(Optional.of(new MergeRequestGroup(GROUP, "owner", "F", List.of(MR_1, MR_2), 1, 1)))
                .thenThrow(new IllegalStateException("down"));
        when(changeRequests.record(anyString())).thenReturn(Optional.empty());
        final var result = service.get("run");
        assertTrue(result.stale());
    }

    @Test
    void testGet_shouldThrowNotFound_whenRunMissing() {
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR, code(() -> service.get("missing")));
    }

    @Test
    void testFresh_shouldCompareLiveRevisions() {
        saved.set(run("running", System.currentTimeMillis()));
        stubLiveMembers();
        assertTrue(service.fresh("run", "execution"));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.fresh("run", "other")));
        when(groups.get(GROUP)).thenReturn(Optional.empty());
        assertFalse(service.fresh("run", "execution"));
    }

    @Test
    void testStart_shouldCreateRunAndDispatch_whenGroupValid() {
        stubLiveMembers();
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of(new ReviewPersonaSnapshot("p", "P", "i", 1)));
        when(store.list()).thenReturn(List.of());
        doAnswer(call -> {
            saved.set(call.getArgument(0));
            return null;
        }).when(store).create(any(GroupAnalysisRun.class));

        final var response = service.start("owner", GROUP, new StartRequest(null, null, null, null, null));

        verify(store).create(any(GroupAnalysisRun.class));
        verify(locks).release(anyString(), anyString());
        assertEquals("queued", response.runStatus());
        assertEquals("https://pipeline/77", response.pipelineUrl());
    }

    @Test
    void testStart_shouldReuseActiveRun_whenSameContext() {
        stubLiveMembers();
        final var existing = run("running", System.currentTimeMillis());
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of(new ReviewPersonaSnapshot("p", "P", "i", 1)));
        when(store.list()).thenReturn(List.of(existing));

        final var response = service.start("owner", GROUP, new StartRequest(null, null, null, null, null));

        assertEquals("execution", response.executionId());
        verify(store, never()).create(any());
    }

    @Test
    void testStart_shouldRejectConflict_whenActiveRunHasDifferentContext() {
        stubLiveMembers();
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of(new ReviewPersonaSnapshot("p", "P", "i", 1)));
        when(store.list()).thenReturn(List.of(run("running", System.currentTimeMillis())));
        final var other = new StartRequest(null, null, "different", null, null);
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.start("owner", GROUP, other)));
        verify(locks).release(anyString(), anyString());
    }

    @Test
    void testStart_shouldFail_whenLockNotAcquired() {
        stubLiveMembers();
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of());
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong()))
                .thenReturn(new LeaderElectionService.Leadership(false, "x", 0));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR,
                code(() -> service.start("owner", GROUP, new StartRequest(null, null, null, null, null))));
    }

    @Test
    void testStart_shouldRejectMissingOrTooSmallGroup() {
        final var request = new StartRequest(null, null, null, null, null);
        when(groups.get("none")).thenReturn(Optional.empty());
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR, code(() -> service.start("owner", "none", request)));
        when(groups.get("tiny")).thenReturn(Optional.of(new MergeRequestGroup("tiny", "owner", "T", List.of(MR_1), 1, 1)));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, code(() -> service.start("owner", "tiny", request)));
    }

    @Test
    void testStart_shouldMarkRunFailed_whenPipelineTriggerFails() {
        stubLiveMembers();
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of());
        when(store.list()).thenReturn(List.of());
        doAnswer(call -> {
            saved.set(call.getArgument(0));
            return null;
        }).when(store).create(any(GroupAnalysisRun.class));
        when(pipelines.triggerPipeline(any())).thenThrow(new IllegalStateException("no pipeline"));

        assertThrows(IllegalStateException.class,
                () -> service.start("owner", GROUP, new StartRequest(null, null, null, null, null)));

        assertEquals("failed", saved.get().result().runStatus());
        assertNotNull(saved.get().result().detail());
    }

    @Test
    void testRetry_shouldRequeueFailedRun_andDiscardFindings() {
        saved.set(run("failed", System.currentTimeMillis()));
        stubLiveMembers();
        when(store.list()).thenReturn(List.of());

        final var response = service.retry("owner", "run");

        assertEquals("queued", saved.get().result().runStatus());
        assertNotEquals("execution", saved.get().result().executionId());
        assertEquals(0, saved.get().sequence());
        assertEquals("https://pipeline/77", response.pipelineUrl());
    }

    @Test
    void testRetry_shouldRejectOtherUserNonRetryableAndStaleRuns() {
        saved.set(run("failed", System.currentTimeMillis()));
        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR, code(() -> service.retry("intruder", "run")));
        saved.set(run("completed", System.currentTimeMillis()));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.retry("owner", "run")));
        saved.set(run("failed", System.currentTimeMillis()));
        when(groups.get(GROUP)).thenReturn(Optional.empty());
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.retry("owner", "run")));
    }

    @Test
    void testRetry_shouldRejectWhenAnotherRunActive() {
        saved.set(run("failed", System.currentTimeMillis()));
        stubLiveMembers();
        when(store.list()).thenReturn(List.of(run("running", System.currentTimeMillis())));
        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, code(() -> service.retry("owner", "run")));
    }

    @Test
    void testUpdate_shouldPublishUsage_whenUsageReported() {
        saved.set(run("running", System.currentTimeMillis()));
        final var usage = new TokenUsage(10, 2, List.of());
        final var update = new GroupAnalysisResponse.Update("execution", "worker", 1, "running", Map.of(), List.of(),
                null, null, null, usage);

        service.update("run", update);

        verify(signals).publish(any());
        assertEquals(Map.of("execution", usage), saved.get().usageByExecution());
    }

    @Test
    void testApplyUpdate_shouldRejectInvalidMemberReviews() {
        final var current = run("running", System.currentTimeMillis());
        final var bad = new GroupAnalysisResponse.MemberReview("other-head", List.of());
        final var update = new GroupAnalysisResponse.Update("execution", "worker", 1, "running",
                Map.of(MR_1, "reviewed"), List.of(), null, Map.of(MR_1, bad), null, null);
        assertThrows(ArnoldError.class, () -> GroupAnalysisService.applyUpdate(current, update));
    }

    @Test
    void testApplyUpdate_shouldMarkRunStale_whenStatusStale() {
        final var current = run("running", System.currentTimeMillis());
        final var next = GroupAnalysisService.applyUpdate(current, new GroupAnalysisResponse.Update("execution", "worker",
                1, "stale", Map.of(), List.of(), null, null, null));
        assertTrue(next.result().stale());
    }
}
