package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueue;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueueItem;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeQueueStore;
import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
            final var queue = new MergeQueue("user-id", List.of(first, second), 100L);
            when(store.getWithVersion(any())).thenReturn(Optional.of(Versioned.of(queue, 3L)));

            final var response = service.reorder("user@example.com", ReorderMergeQueueRequest.builder()
                    .orderedItemIds(List.of("second", "first"))
                    .version(3L)
                    .build());

            assertEquals(List.of("second", "first"), response.items().stream().map(item -> item.id()).toList());
            assertEquals(4L, response.version());
            verify(store).checkAndUpdate(any(MergeQueue.class), any());
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
}
