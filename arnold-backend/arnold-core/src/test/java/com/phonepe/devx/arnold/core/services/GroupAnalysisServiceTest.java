package com.phonepe.devx.arnold.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupAnalysisServiceTest {
    private GroupAnalysisRun run() {
        final var member = new GroupAnalysisResponse.Member("gitlab:42:1", 42, 1, "team/api", "API", "", "https://gitlab.example/team/api/-/merge_requests/1",
                "feature", "main", "base", "start", "head");
        final var response = new GroupAnalysisResponse("run", "group", "Feature", "execution", "queued", List.of(member),
                Map.of(), List.of(), null, null, null, false, 1, 1);
        return new GroupAnalysisRun("run", "owner", new GroupAnalysisResponse.StartRequest(null, null, null, null),
                List.of(), response, null, 0, false, System.currentTimeMillis());
    }

    private GroupAnalysisResponse.Update update(final long sequence, final String status) {
        return new GroupAnalysisResponse.Update("execution", "worker", sequence, status,
                Map.of("gitlab:42:1", "reviewed"), List.of(), null);
    }

    @Test
    void testUpdate_shouldClaimWorkerAndIgnoreDuplicate_whenCallbackRetried() {
        final var next = GroupAnalysisService.applyUpdate(run(), update(1, "running"));
        assertEquals("worker", next.workerId());
        assertEquals(next, GroupAnalysisService.applyUpdate(next, update(1, "running")));
        assertEquals("completed", GroupAnalysisService.applyUpdate(next, update(2, "completed")).result().runStatus());
    }

    @Test
    void testUpdate_shouldNotifyOwner_onceWhenRunFinishes() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var saved = new java.util.concurrent.atomic.AtomicReference<GroupAnalysisRun>(
                GroupAnalysisService.applyUpdate(run(), update(1, "running")));
        when(store.getWithVersion(anyString())).thenAnswer(call ->
                Optional.of(com.livetheoogway.crudstore.core.Versioned.of(saved.get(), 1L)));
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; })
                .when(store).checkAndUpdate(any(GroupAnalysisRun.class), anyLong());
        final var signals = mock(com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher.class);
        final var service = new GroupAnalysisService(store, mock(AerospikeMergeRequestGroupStore.class),
                mock(ChangeRequestService.class), mock(PersonaService.class), mock(PipelineTriggerClient.class),
                mock(LeaderElectionService.class), signals);

        service.update("run", update(2, "completed"));

        final var captor = org.mockito.ArgumentCaptor.forClass(
                com.phonepe.devx.arnold.core.model.events.NotificationRequested.class);
        org.mockito.Mockito.verify(signals).publish(captor.capture());
        assertEquals("group_analysis", captor.getValue().aggregateType());
        assertEquals(Set.of("owner"), captor.getValue().recipientUserIds());
        assertEquals("/history?runId=run", captor.getValue().deepLink());
    }

    @Test
    void testUpdate_shouldRejectDifferentWorkerAndExecution_whenAlreadyClaimed() {
        final var current = GroupAnalysisService.applyUpdate(run(), update(1, "running"));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(current,
                new GroupAnalysisResponse.Update("execution", "other", 2, "running", Map.of(), List.of(), null)));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(current,
                new GroupAnalysisResponse.Update("old-execution", "worker", 2, "running", Map.of(), List.of(), null)));
    }

    @Test
    void testUpdate_shouldRejectRegression_whenRunFinished() {
        final var current = GroupAnalysisService.applyUpdate(run(), update(1, "completed"));
        assertEquals(current, GroupAnalysisService.applyUpdate(current, update(1, "completed")));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(current, update(2, "running")));
    }

    @Test
    void testUpdate_shouldRejectCompletion_whenMemberMissing() {
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                new GroupAnalysisResponse.Update("execution", "worker", 1, "completed", Map.of(), List.of(), null)));
    }

    @Test
    void testUpdate_shouldRejectFinding_whenRevisionOrDestinationInvalid() {
        final var finding = new GroupAnalysisResponse.Finding("finding", "gitlab:42:1", "old-head", "api.java", "api.java",
                1, "Issue", List.of(), "pending", null);
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                new GroupAnalysisResponse.Update("execution", "worker", 1, "running", Map.of(), List.of(finding), null)));
    }

    @Test
    void testGet_shouldHideRun_whenOwnedByAnotherUser() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        when(store.get("run")).thenReturn(Optional.of(run()));
        final var service = new GroupAnalysisService(store, mock(AerospikeMergeRequestGroupStore.class),
                mock(ChangeRequestService.class), mock(PersonaService.class), mock(PipelineTriggerClient.class),
                mock(LeaderElectionService.class), mock(com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher.class));
        assertThrows(RuntimeException.class, () -> service.get("other", "run"));
    }

    @Test
    void testSerialization_shouldPreserveSnapshot_whenStored() throws Exception {
        final var mapper = new ObjectMapper();
        final var run = run();
        final var serialized = mapper.writeValueAsBytes(run);
        assertEquals(run, mapper.readValue(serialized, GroupAnalysisRun.class));
        assertFalse(mapper.readTree(serialized).path("result").has("summary"));
    }

    @Test
    void testUpdate_shouldRejectCallback_whenExecutionExpired() {
        final var expired = run().withExecutionStartedAt(1);
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(expired, update(1, "running")));
    }

    @Test
    void testStart_shouldSnapshotAuthorizedRevisionsAndReuseActiveRun_whenRequestRepeated() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        final var changes = mock(ChangeRequestService.class);
        final var personas = mock(PersonaService.class);
        final var pipelines = mock(PipelineTriggerClient.class);
        final var locks = mock(LeaderElectionService.class);
        final var service = new GroupAnalysisService(store, groups, changes, personas, pipelines, locks, mock(com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher.class));
        final var saved = new java.util.concurrent.atomic.AtomicReference<GroupAnalysisRun>();
        final var group = new com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroup("group", "Feature",
                List.of("gitlab:1:1", "gitlab:2:1"), 1, 1);
        when(groups.get("owner")).thenReturn(Optional.of(new com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroupCollection("owner", List.of(group), 1)));
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of());
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong())).thenReturn(new LeaderElectionService.Leadership(true, "worker", Long.MAX_VALUE));
        when(pipelines.triggerPipeline(anyMap())).thenReturn(77L);
        when(store.list()).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; }).when(store).create(any(GroupAnalysisRun.class));
        when(store.getWithVersion(anyString())).thenAnswer(call -> Optional.of(com.livetheoogway.crudstore.core.Versioned.of(saved.get(), 1L)));
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; }).when(store).checkAndUpdate(any(GroupAnalysisRun.class), anyLong());
        for (final var projectId : List.of(1L, 2L)) {
            final var id = "gitlab:" + projectId + ":1";
            final var url = "https://gitlab.example/repo/" + projectId;
            final var stored = mock(com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord.class);
            when(stored.provider()).thenReturn(com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType.GITLAB);
            when(stored.webUrl()).thenReturn(url);
            when(changes.record(id)).thenReturn(Optional.of(stored));
            final var mr = mock(com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse.class);
            when(mr.id()).thenReturn(id);
            when(mr.repositoryId()).thenReturn(projectId.toString());
            when(mr.number()).thenReturn(1L);
            when(mr.webUrl()).thenReturn(url);
            when(mr.state()).thenReturn("opened");
            when(mr.targetBranch()).thenReturn("main");
            when(mr.headSha()).thenReturn("head-" + projectId);
            when(changes.resolve(any(), eq(url), eq("owner"))).thenReturn(mr);
            when(changes.listDiffVersions(projectId, 1, "owner")).thenReturn(List.of(
                    new com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse(1, "base", "start", "head-" + projectId, 1, "commit")));
        }
        final var request = new GroupAnalysisResponse.StartRequest(null, List.of(), "Feature context", List.of());
        final var first = service.start("owner", "group", request);
        assertEquals(2, first.members().size());
        assertEquals("base", first.members().get(1).baseSha());
        assertEquals("head-2", first.members().get(1).headSha());
        assertEquals(77L, first.pipelineId());
        assertEquals(first.runId(), service.start("owner", "group", request).runId());
        verify(pipelines, times(1)).triggerPipeline(argThat(variables -> "group_review".equals(variables.get("WORKFLOW"))
                && first.executionId().equals(variables.get("EXECUTION_ID"))));
    }

    @Test
    void testRetry_shouldRejectStaleMembership_whenGroupDeleted() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        final var pipelines = mock(PipelineTriggerClient.class);
        final var finding = new GroupAnalysisResponse.Finding("finding", "gitlab:42:1", "head", null, null,
                null, "Issue", List.of(), "pending", null);
        final var saved = run();
        when(store.get("run")).thenReturn(Optional.of(saved.withResult(saved.result().withRunStatus("partial")
                .withFindings(List.of(finding)))));
        when(groups.get("owner")).thenReturn(Optional.empty());
        final var service = new GroupAnalysisService(store, groups, mock(ChangeRequestService.class),
                mock(PersonaService.class), pipelines, mock(LeaderElectionService.class),
                mock(com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher.class));
        assertThrows(RuntimeException.class, () -> service.retryPublication("owner", "run"));
        verifyNoInteractions(pipelines);
    }
}
