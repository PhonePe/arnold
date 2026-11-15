/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.arnold.models.mergequeue.CreateMergeRequestGroupRequest;
import com.phonepe.arnold.models.mergequeue.UpdateMergeRequestGroupRequest;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MergeRequestGroupServiceImplCoverageTest {
    private final AerospikeMergeRequestGroupStore store = mock(AerospikeMergeRequestGroupStore.class);
    private final UserMergeRequestService userMergeRequestService = mock(UserMergeRequestService.class);
    private final MergeQueueService mergeQueueService = mock(MergeQueueService.class);
    private final MergeRequestGroupServiceImpl service =
            new MergeRequestGroupServiceImpl(store, userMergeRequestService, mergeQueueService);

    private static MergeRequestGroup group(final String id, final String owner, final long createdAt) {
        return new MergeRequestGroup(id, owner, "name-" + id, List.of("a", "b"), createdAt, createdAt);
    }

    private void summariesFor(final String... ids) {
        final var map = new java.util.HashMap<String, UserMergeRequestResponse>();
        for (final var id : ids) {
            map.put(id, mock(UserMergeRequestResponse.class));
        }
        when(userMergeRequestService.summaries(anyString(), anyList())).thenAnswer(invocation -> {
            final List<String> requested = invocation.getArgument(1);
            final var result = new java.util.HashMap<String, UserMergeRequestResponse>();
            requested.stream().filter(map::containsKey).forEach(id -> result.put(id, map.get(id)));
            return result;
        });
    }

    private static void assertCode(final ArnoldErrorCode code, final Runnable runnable) {
        assertEquals(code, assertThrows(ArnoldError.class, runnable::run).getErrorCode());
    }

    @Test
    void testGet_shouldReturnResponseWithResolvedMembersOnly() {
        summariesFor("a");
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u1", 5L)));

        final var response = service.get("g1");

        assertEquals("g1", response.id());
        assertEquals(1, response.members().size());
    }

    @Test
    void testGet_shouldThrowNotFound_whenGroupMissing() {
        when(store.get("g1")).thenReturn(Optional.empty());

        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.get("g1"));
    }

    @Test
    void testList_shouldFilterByOwnerAndSortNewestFirst() {
        summariesFor("a", "b");
        when(store.list()).thenReturn(List.of(group("old", "u1", 1L), group("other", "u2", 9L),
                group("new", "u1", 7L)));

        final var response = service.list("u1");

        assertEquals(List.of("new", "old"), response.groups().stream().map(g -> g.id()).toList());
    }

    @Test
    void testCreate_shouldPersistGroupAndReplaceQueueMembers() {
        summariesFor("a", "b");
        when(store.list()).thenReturn(List.of());

        final var response = service.create("u1", CreateMergeRequestGroupRequest.builder()
                .name("  My group ").mergeRequestIds(List.of("a", "b")).build());

        assertEquals(0, response.groups().size());
        verify(store).create(any(MergeRequestGroup.class));
        verify(mergeQueueService).replaceMembersWithGroup(eq("u1"), anyString(), eq(List.of("a", "b")));
    }

    @Test
    void testCreate_shouldThrowConflict_whenStoreRejectsCreate() {
        summariesFor("a", "b");
        doThrow(new IllegalStateException("exists")).when(store).create(any());

        assertCode(ArnoldErrorCode.CONFLICT_ERROR, () -> service.create("u1",
                CreateMergeRequestGroupRequest.builder().name("n").mergeRequestIds(List.of("a", "b")).build()));
        verify(mergeQueueService, never()).replaceMembersWithGroup(anyString(), anyString(), anyList());
    }

    @Test
    void testCreate_shouldRejectInvalidMemberSets() {
        summariesFor("a", "b");
        final var nullIds = CreateMergeRequestGroupRequest.builder().name("n").mergeRequestIds(null).build();
        final var single = CreateMergeRequestGroupRequest.builder().name("n").mergeRequestIds(List.of("a")).build();
        final var duplicates = CreateMergeRequestGroupRequest.builder().name("n")
                .mergeRequestIds(List.of("a", "a", "b")).build();
        final var unknown = CreateMergeRequestGroupRequest.builder().name("n")
                .mergeRequestIds(List.of("a", "zzz")).build();

        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR, () -> service.create("u1", nullIds));
        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR, () -> service.create("u1", single));
        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR, () -> service.create("u1", duplicates));
        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR, () -> service.create("u1", unknown));
    }

    @Test
    void testUpdate_shouldCheckAndUpdateWithVersionAndReplaceMembers() {
        summariesFor("a", "c");
        final var current = group("g1", "u1", 5L);
        when(store.get("g1")).thenReturn(Optional.of(current));
        when(store.getWithVersion("g1")).thenReturn(Optional.of(Versioned.of(current, 3L)));

        final var response = service.update("u1", "g1", UpdateMergeRequestGroupRequest.builder()
                .name(" renamed ").mergeRequestIds(List.of("a", "c")).build());

        assertEquals("renamed", response.name());
        verify(store).checkAndUpdate(any(MergeRequestGroup.class), eq(3L));
        verify(mergeQueueService).replaceMembersWithGroup("u1", "g1", List.of("a", "c"));
    }

    @Test
    void testUpdate_shouldRetryOnConcurrentUpdate() {
        summariesFor("a", "c");
        final var current = group("g1", "u1", 5L);
        when(store.get("g1")).thenReturn(Optional.of(current));
        when(store.getWithVersion("g1")).thenReturn(Optional.of(Versioned.of(current, 3L)));
        doThrow(new ConcurrentUpdateException("race")).doNothing().when(store).checkAndUpdate(any(), anyLong());

        service.update("u1", "g1", UpdateMergeRequestGroupRequest.builder()
                .name("renamed").mergeRequestIds(List.of("a", "c")).build());

        verify(store, times(2)).checkAndUpdate(any(), eq(3L));
    }

    @Test
    void testUpdate_shouldThrowConflict_whenRetriesExhausted() {
        summariesFor("a", "c");
        final var current = group("g1", "u1", 5L);
        when(store.get("g1")).thenReturn(Optional.of(current));
        when(store.getWithVersion("g1")).thenReturn(Optional.of(Versioned.of(current, 3L)));
        doThrow(new ConcurrentUpdateException("race")).when(store).checkAndUpdate(any(), anyLong());
        final var request = UpdateMergeRequestGroupRequest.builder()
                .name("renamed").mergeRequestIds(List.of("a", "c")).build();

        assertCode(ArnoldErrorCode.CONFLICT_ERROR, () -> service.update("u1", "g1", request));
        verify(store, times(8)).checkAndUpdate(any(), anyLong());
    }

    @Test
    void testUpdate_shouldThrowNotFound_whenVersionedReadMissing() {
        summariesFor("a", "c");
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u1", 5L)));
        when(store.getWithVersion("g1")).thenReturn(Optional.empty());
        final var request = UpdateMergeRequestGroupRequest.builder()
                .name("n").mergeRequestIds(List.of("a", "c")).build();

        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.update("u1", "g1", request));
    }

    @Test
    void testUpdate_shouldThrowNotFound_whenCallerIsNotOwner() {
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u2", 5L)));
        final var request = UpdateMergeRequestGroupRequest.builder()
                .name("n").mergeRequestIds(List.of("a", "c")).build();

        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.update("u1", "g1", request));
    }

    @Test
    void testDelete_shouldRemoveGroupAndReconcileQueue() {
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u1", 5L)));
        when(userMergeRequestService.associationsForUser("u1")).thenReturn(List.of());

        service.delete("u1", "g1", 0L);

        verify(store).delete("g1");
        verify(mergeQueueService).removeGroup("u1", "g1");
        verify(mergeQueueService).reconcile("u1", List.of());
    }

    @Test
    void testDelete_shouldThrowNotFound_whenStoreDeleteFails() {
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u1", 5L)));
        doThrow(new IllegalStateException("gone")).when(store).delete("g1");

        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.delete("u1", "g1", 0L));
        verify(mergeQueueService, never()).removeGroup(anyString(), anyString());
    }

    @Test
    void testDelete_shouldThrowNotFound_whenNotOwner() {
        when(store.get("g1")).thenReturn(Optional.of(group("g1", "u2", 5L)));

        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.delete("u1", "g1", 0L));
        verify(store, never()).delete(anyString());
    }
}
