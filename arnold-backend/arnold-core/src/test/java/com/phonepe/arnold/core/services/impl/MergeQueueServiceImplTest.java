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

package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.model.mergequeue.MergeQueue;
import com.phonepe.arnold.core.model.mergequeue.MergeQueueItem;
import com.phonepe.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeQueueStore;
import com.phonepe.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.arnold.models.mergequeue.ReorderMergeQueueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueItemResponse;
import com.phonepe.arnold.models.mergequeue.MergeRequestGroupResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MergeQueueServiceImplTest {
    private AerospikeMergeQueueStore store;
    private MergeQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        store = mock(AerospikeMergeQueueStore.class);
        service = new MergeQueueServiceImpl(store);
    }

    @Test
    void testAdd_shouldCreateQueue_whenQueueDoesNotExist() {
            when(store.getWithVersion(any())).thenReturn(Optional.empty());
            final var request = AddMergeQueueItemRequest.builder()
                    .mergeRequestId("10:12")
                    .role("reviewer")
                    .build();

            final var response = service.add("user@example.com", request);

            assertEquals(1, response.items().size());
            assertEquals("10:12", response.items().get(0).mergeRequestId());
            verify(store).create(any(MergeQueue.class));
    }

    @Test
    void testAdd_shouldNotDuplicateItem_whenMergeRequestAlreadyQueued() {
            final var item = new MergeQueueItem("item-1", "10:12", "reviewer", false);
            final var queue = new MergeQueue("user-id", List.of(item), 100L);
            when(store.getWithVersion(any())).thenReturn(Optional.of(Versioned.of(queue, 4L)));
            final var request = AddMergeQueueItemRequest.builder()
                    .mergeRequestId("10:12")
                    .role("reviewer")
                    .build();

            final var response = service.add("user@example.com", request);

            assertEquals(1, response.items().size());
            assertEquals(4L, response.version());
            verify(store, never()).checkAndUpdate(any(), any());
    }

    @Test
    void testReorder_shouldPersistOrder_whenVersionMatches() {
            final var first = new MergeQueueItem("first", "10:1", "author", false);
            final var second = new MergeQueueItem("second", "10:2", "reviewer", false);
            final var queue = new MergeQueue("user-id", List.of(first, second), 100L, List.of("10:2", "10:1"));
            when(store.getWithVersion(any())).thenReturn(Optional.of(Versioned.of(queue, 3L)));

            final var response = service.reorder("user@example.com", ReorderMergeQueueRequest.builder()
                    .orderedItemIds(List.of("second", "first"))
                    .version(3L)
                    .build());

            assertEquals(List.of("second", "first"), response.items().stream().map(item -> item.id()).toList());
            assertEquals(4L, response.version());
            final var saved = ArgumentCaptor.forClass(MergeQueue.class);
            verify(store).checkAndUpdate(saved.capture(), any());
            assertEquals(queue.priorityOrder(), saved.getValue().priorityOrder());
    }

    @Test
    void testReorder_shouldRejectUpdate_whenVersionIsStale() {
            final var queue = new MergeQueue("user-id",
                    List.of(new MergeQueueItem("first", "10:1", "author", false)), 100L);
            when(store.getWithVersion(any())).thenReturn(Optional.of(Versioned.of(queue, 3L)));

            assertThrows(RuntimeException.class, () -> service.reorder("user@example.com",
                    ReorderMergeQueueRequest.builder().orderedItemIds(List.of("first")).version(2L).build()));
            verify(store, never()).checkAndUpdate(any(), any());
    }
    @Test
    void testReorderPriority_shouldSaveIndividualGroupMembers_whenVersionMatches() {
        final var groupItem = new MergeQueueItem("group-item", "GROUP", "group", "author", "MANUAL", false);
        final var queue = new MergeQueue("user", List.of(groupItem), 100L);
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(queue, 3L)));
        service = spy(service);
        doReturn(priorityQueue()).when(service).getEnriched("user");

        service.reorderPriority("user", ReorderMergeQueueRequest.builder()
                .orderedItemIds(List.of("gitlab:10:2", "gitlab:10:1")).version(3L).build());

        final var saved = ArgumentCaptor.forClass(MergeQueue.class);
        verify(store).checkAndUpdate(saved.capture(), org.mockito.ArgumentMatchers.eq(3L));
        assertEquals("user", saved.getValue().id());
        assertEquals(List.of("gitlab:10:2", "gitlab:10:1"), saved.getValue().priorityOrder());
        assertEquals(queue.items(), saved.getValue().items());
    }

    @Test
    void testReorderPriority_shouldRejectInvalidIdsOrVersion_whenRequestIsStaleOrUnrelated() {
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(new MergeQueue("user", List.of(), 0), 3L)));
        service = spy(service);
        doReturn(priorityQueue()).when(service).getEnriched("user");
        for (final var ids : List.of(List.of("gitlab:10:1"), List.of("gitlab:10:1", "gitlab:10:1"),
                List.of("gitlab:10:1", "other-users-mr"))) {
            assertThrows(RuntimeException.class, () -> service.reorderPriority("user",
                    ReorderMergeQueueRequest.builder().orderedItemIds(ids).version(3L).build()));
        }
        assertThrows(RuntimeException.class, () -> service.reorderPriority("user", ReorderMergeQueueRequest.builder()
                .orderedItemIds(List.of("gitlab:10:2", "gitlab:10:1")).version(2L).build()));
        verify(store, never()).checkAndUpdate(any(), any());
    }

    @Test
    void testReorderPriority_shouldReportConflict_whenConcurrentWriteWins() {
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(new MergeQueue("user", List.of(), 0), 3L)));
        service = spy(service);
        doReturn(priorityQueue()).when(service).getEnriched("user");
        doThrow(com.livetheoogway.crudstore.core.ConcurrentUpdateException.class).when(store).checkAndUpdate(any(), any());
        assertThrows(RuntimeException.class, () -> service.reorderPriority("user", ReorderMergeQueueRequest.builder()
                .orderedItemIds(List.of("gitlab:10:2", "gitlab:10:1")).version(3L).build()));
    }

    @Test
    void testPriorityOrder_shouldSurviveQueueMutationsAndSerialization_whenAlreadySaved() throws Exception {
        final var order = List.of("gitlab:10:2", "gitlab:10:1");
        final var queue = new MergeQueue("user", List.of(), 0, order);
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(queue, 3L)));
        assertEquals(order, service.getEnriched("user").priorityOrder());
        service.add("user", AddMergeQueueItemRequest.builder().mergeRequestId("gitlab:10:3").role("author").build());
        service.remove("user", "missing");
        service.replaceMembersWithGroup("user", "group", List.of("gitlab:10:1", "gitlab:10:2"));
        service.removeGroup("user", "group");
        service = new MergeQueueServiceImpl(store, mock(AerospikeMergeRequestGroupStore.class), null);
        service.reconcile("user", List.of(new UserChangeRequest("user:mr", "user", "gitlab:10:3",
                Set.of(MergeRequestRole.AUTHOR), true, true, 0, 0, 0)));
        final var saved = ArgumentCaptor.forClass(MergeQueue.class);
        verify(store, org.mockito.Mockito.times(5)).checkAndUpdate(saved.capture(), any());
        saved.getAllValues().forEach(value -> assertEquals(order, value.priorityOrder()));
        final var mapper = new ObjectMapper();
        assertEquals(queue, mapper.readValue(mapper.writeValueAsString(queue), MergeQueue.class));
        assertEquals(List.of(), mapper.readValue("{\"id\":\"old\",\"items\":[],\"updatedAt\":0}", MergeQueue.class).priorityOrder());
    }

    @Test
    void testReconcile_shouldKeepGroupWithForeignMembersAndDropClosedOwnGroups() {
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        service = new MergeQueueServiceImpl(store, groups, null);
        final var queue = new MergeQueue("user", List.of(
                new MergeQueueItem("foreign-item", "GROUP", "foreign", "author", "MANUAL", false),
                new MergeQueueItem("closed-item", "GROUP", "closed", "author", "MANUAL", false)), 0);
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(queue, 1L)));
        when(groups.get(List.of("foreign", "closed"))).thenReturn(Map.of(
                "foreign", new MergeRequestGroup("foreign", "user", "Foreign", List.of("gitlab:10:8", "gitlab:10:9"), 0, 0),
                "closed", new MergeRequestGroup("closed", "user", "Closed", List.of("gitlab:10:1"), 0, 0)));

        service.reconcile("user", List.of(new UserChangeRequest("user:gitlab:10:1", "user", "gitlab:10:1",
                Set.of(MergeRequestRole.AUTHOR), true, false, 0, 0, 0)));

        final var saved = ArgumentCaptor.forClass(MergeQueue.class);
        verify(store).checkAndUpdate(saved.capture(), any());
        assertEquals(List.of("foreign"), saved.getValue().items().stream().map(MergeQueueItem::targetId).toList());
    }

    @Test
    void testGetEnriched_shouldHideFilteredMembersAndEmptyGroups_whenProjectsAreArchived() {
        final var groups = mock(AerospikeMergeRequestGroupStore.class);
        final var requests = mock(UserMergeRequestService.class);
        service = new MergeQueueServiceImpl(store, groups, requests);
        final var queue = new MergeQueue("user", List.of(
                new MergeQueueItem("archived-item", "archived", "author", false),
                new MergeQueueItem("mixed-item", "GROUP", "mixed", "author", "MANUAL", false),
                new MergeQueueItem("empty-item", "GROUP", "empty", "author", "MANUAL", false)), 100);
        when(store.getWithVersion("user")).thenReturn(Optional.of(Versioned.of(queue, 1L)));
        when(groups.get(List.of("mixed", "empty"))).thenReturn(Map.of(
                "mixed", new MergeRequestGroup("mixed", "user", "Mixed", List.of("active", "archived"), 0, 0),
                "empty", new MergeRequestGroup("empty", "user", "Empty", List.of("archived"), 0, 0)));
        final var active = new UserMergeRequestResponse(mock(MergeRequestResponse.class), Set.of("author"), "CREATED", false);
        when(requests.summaries(org.mockito.ArgumentMatchers.eq("user"), any())).thenReturn(Map.of("active", active));

        final var result = service.getEnriched("user");

        assertEquals(1, result.items().size());
        assertEquals("mixed", result.items().get(0).targetId());
        assertEquals(List.of(active), result.items().get(0).group().members());
    }

    private EnrichedMergeQueueResponse priorityQueue() {
        final var first = mock(MergeRequestResponse.class);
        final var second = mock(MergeRequestResponse.class);
        when(first.id()).thenReturn("gitlab:10:1");
        when(second.id()).thenReturn("gitlab:10:2");
        when(first.state()).thenReturn("opened");
        when(second.state()).thenReturn("opened");
        final var group = new MergeRequestGroupResponse("group", "Review group", List.of(
                new UserMergeRequestResponse(first, Set.of("author"), "CREATED", false),
                new UserMergeRequestResponse(second, Set.of("reviewer"), "CREATED", false)), 0, 0);
        return new EnrichedMergeQueueResponse(List.of(new EnrichedMergeQueueItemResponse("group-item", "GROUP",
                "group", "author", "MANUAL", false, null, group)), 0, 3L, 1L, List.of());
    }

}
