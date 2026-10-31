/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse;
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
                Map.of(), List.of(), List.of(), null, null, null, false, 1, 1);
        return new GroupAnalysisRun("run", "owner", new GroupAnalysisResponse.StartRequest(null, null, null, null),
                List.of(), response, null, 0, false, System.currentTimeMillis(), Map.of(), false, Map.of());
    }

    private GroupAnalysisResponse.Update update(final long sequence, final String status) {
        return new GroupAnalysisResponse.Update("execution", "worker", sequence, status,
                Map.of("gitlab:42:1", "reviewed"), List.of(), null, null, null);
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
        final var signals = mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class);
        final var service = new GroupAnalysisService(store, mock(AerospikeMergeRequestGroupStore.class),
                mock(ChangeRequestService.class), mock(PersonaService.class), mock(RunnerDispatcher.class),
                mock(LeaderElectionService.class), signals);

        service.update("run", update(2, "completed"));

        final var captor = org.mockito.ArgumentCaptor.forClass(
                com.phonepe.arnold.core.model.events.NotificationRequested.class);
        org.mockito.Mockito.verify(signals).publish(captor.capture());
        assertEquals("group_analysis", captor.getValue().aggregateType());
        assertEquals(Set.of("owner"), captor.getValue().recipientUserIds());
        assertEquals("/history?runId=run", captor.getValue().deepLink());
    }

    @Test
    void testUpdate_shouldRejectDifferentWorkerAndExecution_whenAlreadyClaimed() {
        final var current = GroupAnalysisService.applyUpdate(run(), update(1, "running"));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(current,
                new GroupAnalysisResponse.Update("execution", "other", 2, "running", Map.of(), List.of(), null, null, null)));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(current,
                new GroupAnalysisResponse.Update("old-execution", "worker", 2, "running", Map.of(), List.of(), null, null, null)));
    }

    @Test
    void testUpdate_shouldKeepLatestUsagePerExecution_whenCallbacksReportRunningTotals() {
        final var usage = new com.phonepe.arnold.models.analysis.TokenUsage(10, 2, List.of());
        final var later = new com.phonepe.arnold.models.analysis.TokenUsage(30, 5, List.of());
        final var first = GroupAnalysisService.applyUpdate(run(), new GroupAnalysisResponse.Update("execution", "worker",
                1, "running", Map.of(), List.of(), null, null, null, usage));
        final var next = GroupAnalysisService.applyUpdate(first, new GroupAnalysisResponse.Update("execution", "worker",
                2, "running", Map.of(), List.of(), null, null, null, later));
        assertEquals(Map.of("execution", later), next.usageByExecution());
        assertEquals(Map.of("execution", later), GroupAnalysisService.applyUpdate(next, update(3, "completed")).usageByExecution());
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
                new GroupAnalysisResponse.Update("execution", "worker", 1, "completed", Map.of(), List.of(), null, null, null)));
    }

    @Test
    void testUpdate_shouldRejectFinding_whenRevisionOrDestinationInvalid() {
        final var finding = new GroupAnalysisResponse.Finding("finding", "gitlab:42:1", "old-head", "api.java", "api.java",
                1, "Issue", List.of(), "pending", null);
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                new GroupAnalysisResponse.Update("execution", "worker", 1, "running", Map.of(), List.of(finding), null, null, null)));
    }

    @Test
    void testGet_shouldReturnRun_whenCallerDidNotInvokeIt() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        when(store.get("run")).thenReturn(Optional.of(run()));
        final var service = new GroupAnalysisService(store, mock(AerospikeMergeRequestGroupStore.class),
                mock(ChangeRequestService.class), mock(PersonaService.class), mock(RunnerDispatcher.class),
                mock(LeaderElectionService.class), mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class));
        assertEquals("run", service.get("run").runId());
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
    void testGet_shouldAdvanceResponseTimestamp_whenExecutionTimesOut() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var service = new GroupAnalysisService(store, mock(AerospikeMergeRequestGroupStore.class),
                mock(ChangeRequestService.class), mock(PersonaService.class), mock(RunnerDispatcher.class),
                mock(LeaderElectionService.class), mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class));
        final var expired = run().withExecutionStartedAt(System.currentTimeMillis() - 3 * 60 * 60 * 1000L - 1000);
        when(store.get("run")).thenReturn(Optional.of(expired));
        when(store.list()).thenReturn(List.of(expired));

        final var detail = service.get("run");
        final var listed = service.list(null, null, 50).get(0);

        assertEquals("failed", detail.runStatus());
        assertTrue(detail.updatedAt() > expired.result().updatedAt());
        assertEquals(expired.executionStartedAt() + 3 * 60 * 60 * 1000L, detail.updatedAt());
        assertEquals(detail.updatedAt(), listed.updatedAt());
        assertEquals(detail.updatedAt(), service.get("run").updatedAt());
    }

    @Test
    void testStart_shouldSnapshotAuthorizedRevisionsAndReuseActiveRun_whenRequestRepeated() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        final var changes = mock(ChangeRequestService.class);
        final var personas = mock(PersonaService.class);
        final var pipelines = mock(RunnerDispatcher.class);
        final var locks = mock(LeaderElectionService.class);
        final var service = new GroupAnalysisService(store, groups, changes, personas, pipelines, locks, mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class));
        final var saved = new java.util.concurrent.atomic.AtomicReference<GroupAnalysisRun>();
        final var group = new com.phonepe.arnold.core.model.mergequeue.MergeRequestGroup("group", "owner", "Feature",
                List.of("gitlab:1:1", "gitlab:2:1"), 1, 1);
        when(groups.get("group")).thenReturn(Optional.of(group));
        when(personas.resolveForAnalysis(eq("owner"), any())).thenReturn(List.of(
                new com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot(
                        "security", "Security", "Review security risks", 2)));
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong())).thenReturn(new LeaderElectionService.Leadership(true, "worker", Long.MAX_VALUE));
        when(pipelines.submit(anyMap())).thenReturn(77L);
        when(store.list()).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; }).when(store).create(any(GroupAnalysisRun.class));
        when(store.getWithVersion(anyString())).thenAnswer(call -> Optional.of(com.livetheoogway.crudstore.core.Versioned.of(saved.get(), 1L)));
        doAnswer(call -> { saved.set(call.getArgument(0)); return null; }).when(store).checkAndUpdate(any(GroupAnalysisRun.class), anyLong());
        for (final var projectId : List.of(1L, 2L)) {
            final var id = "gitlab:" + projectId + ":1";
            final var url = "https://gitlab.example/repo/" + projectId;
            final var stored = mock(com.phonepe.arnold.core.model.vcs.ChangeRequestRecord.class);
            when(stored.provider()).thenReturn(com.phonepe.arnold.core.model.vcs.VersionControlProviderType.GITLAB);
            when(stored.webUrl()).thenReturn(url);
            when(changes.record(id)).thenReturn(Optional.of(stored));
            final var mr = mock(com.phonepe.arnold.models.mergerequest.MergeRequestResponse.class);
            when(mr.id()).thenReturn(id);
            when(mr.repositoryId()).thenReturn(projectId.toString());
            when(mr.number()).thenReturn(1L);
            when(mr.webUrl()).thenReturn(url);
            when(mr.state()).thenReturn("opened");
            when(mr.targetBranch()).thenReturn("main");
            when(mr.headSha()).thenReturn("head-" + projectId);
            when(changes.resolve(any(), eq(url), eq("owner"))).thenReturn(mr);
            when(changes.listDiffVersions(VersionControlProviderType.GITLAB, projectId, 1, "owner")).thenReturn(List.of(
                    new com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse(1, "base", "start", "head-" + projectId, 1, "commit")));
        }
        final var request = new GroupAnalysisResponse.StartRequest("https://docs.example/rfc", List.of("ACME-123"), "Feature context", List.of("security"));
        final var first = service.start("owner", "group", request);
        assertEquals(2, first.members().size());
        assertEquals("base", first.members().get(1).baseSha());
        assertEquals("head-2", first.members().get(1).headSha());
        assertEquals(77L, first.pipelineId());
        assertEquals(List.of(new com.phonepe.arnold.models.analysis.AnalysisPersonaResponse(
                "security", "Security", 2)), first.reviewPersonas());
        assertEquals(first.runId(), service.start("owner", "group", request).runId());
        verify(pipelines, times(1)).submit(argThat(variables -> "group_review".equals(variables.get("WORKFLOW"))
                && first.executionId().equals(variables.get("EXECUTION_ID"))));

        when(store.get(first.runId())).thenAnswer(call -> Optional.of(saved.get()));
        final var reviewed = new GroupAnalysisResponse.MemberReview("head-1", List.of());
        service.update(first.runId(), new GroupAnalysisResponse.Update(first.executionId(), "worker", 1, "failed",
                Map.of("gitlab:1:1", "reviewed", "gitlab:2:1", "failed"), List.of(), "Member failed",
                Map.of("gitlab:1:1", reviewed), false));
        clearInvocations(personas);

        final var newer = saved.get().withId("newer-run").withResult(first.withRunId("newer-run"));
        when(store.list()).thenAnswer(call -> List.of(saved.get(), newer));
        clearInvocations(locks, pipelines, store);
        assertThrows(RuntimeException.class, () -> service.retry("owner", first.runId()));
        verify(pipelines, never()).submit(anyMap());
        verify(store, never()).checkAndUpdate(any(GroupAnalysisRun.class), any());
        final var order = inOrder(locks, store);
        order.verify(locks).acquireOrRenew(eq("group-analysis:group"), anyString(), eq(120_000L));
        order.verify(store).list();
        order.verify(locks).release(eq("group-analysis:group"), anyString());

        when(store.list()).thenAnswer(call -> List.of(saved.get()));
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong()))
                .thenReturn(new LeaderElectionService.Leadership(false, "other-worker", Long.MAX_VALUE));
        clearInvocations(locks, store);
        assertThrows(RuntimeException.class, () -> service.retry("owner", first.runId()));
        verify(pipelines, never()).submit(anyMap());
        verify(store, never()).list();
        verify(locks, never()).release(anyString(), anyString());
        when(locks.acquireOrRenew(anyString(), anyString(), anyLong()))
                .thenReturn(new LeaderElectionService.Leadership(true, "worker", Long.MAX_VALUE));
        clearInvocations(locks, store);
        final var retry = service.retry("owner", first.runId());
        final var retryOrder = inOrder(locks, store, pipelines);
        retryOrder.verify(locks).acquireOrRenew(eq("group-analysis:group"), anyString(), eq(120_000L));
        retryOrder.verify(store).list();
        retryOrder.verify(store).checkAndUpdate(argThat(run -> "queued".equals(run.result().runStatus())
                && run.result().pipelineId() == null), any());
        retryOrder.verify(pipelines).submit(anyMap());
        retryOrder.verify(locks).release(eq("group-analysis:group"), anyString());
        final var manifest = service.manifest(first.runId(), retry.executionId());
        assertNotEquals(first.executionId(), retry.executionId());
        assertEquals(first.members(), manifest.run().members());
        assertEquals(request, manifest.context());
        assertEquals("Review security risks", manifest.reviewPersonas().get(0).instructions());
        assertEquals(2, manifest.reviewPersonas().get(0).version());
        assertEquals(Map.of("gitlab:1:1", reviewed), manifest.memberReviews());
        assertFalse(manifest.publicationOnly());
        assertThrows(RuntimeException.class, () -> service.update(first.runId(),
                new GroupAnalysisResponse.Update(first.executionId(), "worker", 2, "running",
                        Map.of(), List.of(), null, null, null)));
        verifyNoInteractions(personas);

        final var finding = new GroupAnalysisResponse.Finding("finding", "gitlab:2:1", "head-2", null, null,
                null, "Issue", List.of(), "pending", null);
        service.update(first.runId(), new GroupAnalysisResponse.Update(retry.executionId(), "next-worker", 1, "partial",
                Map.of("gitlab:1:1", "reviewed", "gitlab:2:1", "reviewed"), List.of(finding), null,
                Map.of("gitlab:2:1", new GroupAnalysisResponse.MemberReview("head-2", List.of(Map.of("suggestion_content", "Issue")))), true));
        final var publicationRetry = service.retry("owner", first.runId());
        final var publicationManifest = service.manifest(first.runId(), publicationRetry.executionId());
        assertTrue(publicationManifest.publicationOnly());
        assertEquals(List.of(finding), publicationManifest.run().findings());
        assertEquals(request, publicationManifest.context());
        assertEquals(2, publicationManifest.memberReviews().size());
        verifyNoInteractions(personas);

        saved.set(saved.get().withResult(saved.get().result().withRunStatus("failed").withFindings(List.of())));
        final var emptyRetry = service.retry("owner", first.runId());
        assertTrue(service.manifest(first.runId(), emptyRetry.executionId()).publicationOnly());

        saved.set(saved.get().withMemberReviews(Map.of()).withAnalysisComplete(false)
                .withResult(saved.get().result().withRunStatus("failed").withFindings(List.of(finding))));
        final var legacyRetry = service.retry("owner", first.runId());
        final var legacyManifest = service.manifest(first.runId(), legacyRetry.executionId());
        assertFalse(legacyManifest.publicationOnly());
        assertTrue(legacyManifest.run().findings().isEmpty());
        assertEquals(request, legacyManifest.context());

    }

    @Test
    void testUpdate_shouldValidateAndRetainMemberReviews_whenCheckpointing() throws Exception {
        final var review = new GroupAnalysisResponse.MemberReview("head", List.of(Map.of("suggestion_content", "Issue")));
        final var checkpoint = update(1, "running").withMemberReviews(Map.of("gitlab:42:1", review));
        final var saved = GroupAnalysisService.applyUpdate(run(), checkpoint);
        final var continued = GroupAnalysisService.applyUpdate(saved, update(2, "running"));
        assertEquals(saved.memberReviews(), continued.memberReviews());
        final var mapper = new ObjectMapper();
        assertEquals(saved, mapper.readValue(mapper.writeValueAsBytes(saved), GroupAnalysisRun.class));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                checkpoint.withMemberReviews(Map.of("unknown", review))));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                checkpoint.withMemberReviews(Map.of("gitlab:42:1", new GroupAnalysisResponse.MemberReview("old", List.of())))));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(saved,
                update(2, "running").withMemberReviews(Map.of("gitlab:42:1", new GroupAnalysisResponse.MemberReview("head", List.of())))));
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(run(),
                update(1, "running").withAnalysisComplete(true)));
        final var ready = GroupAnalysisService.applyUpdate(saved, update(2, "running").withAnalysisComplete(true));
        assertTrue(ready.analysisComplete());
        assertThrows(RuntimeException.class, () -> GroupAnalysisService.applyUpdate(ready,
                update(3, "running").withAnalysisComplete(false)));
        final var legacy = mapper.valueToTree(run());
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove(List.of("memberReviews", "analysisComplete"));
        final var restored = mapper.treeToValue(legacy, GroupAnalysisRun.class);
        assertEquals(Map.of(), restored.memberReviews());
        assertFalse(restored.analysisComplete());
    }

    @Test
    void testRetry_shouldRejectStaleMembership_whenGroupDeleted() {
        final var store = mock(AerospikeGroupAnalysisRunStore.class);
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        final var pipelines = mock(RunnerDispatcher.class);
        final var finding = new GroupAnalysisResponse.Finding("finding", "gitlab:42:1", "head", null, null,
                null, "Issue", List.of(), "pending", null);
        final var saved = run();
        when(store.get("run")).thenReturn(Optional.of(saved.withResult(saved.result().withRunStatus("partial")
                .withFindings(List.of(finding)))));
        when(groups.get("group")).thenReturn(Optional.empty());
        final var service = new GroupAnalysisService(store, groups, mock(ChangeRequestService.class),
                mock(PersonaService.class), pipelines, mock(LeaderElectionService.class),
                mock(com.phonepe.arnold.core.events.ArnoldSignalPublisher.class));
        assertThrows(RuntimeException.class, () -> service.retry("owner", "run"));
        verifyNoInteractions(pipelines);
    }
}
