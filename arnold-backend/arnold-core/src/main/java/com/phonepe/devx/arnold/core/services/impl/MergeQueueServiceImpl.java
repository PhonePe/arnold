package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueue;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeQueueItem;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeMergeQueueStore;
import com.phonepe.devx.arnold.models.mergequeue.AddMergeQueueItemRequest;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueItemResponse;
import com.phonepe.devx.arnold.models.mergequeue.MergeQueueResponse;
import com.phonepe.devx.arnold.models.mergequeue.ReorderMergeQueueRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Singleton
public class MergeQueueServiceImpl implements MergeQueueService {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

    private final AerospikeMergeQueueStore mergeQueueStore;

    @Inject
    public MergeQueueServiceImpl(final AerospikeMergeQueueStore mergeQueueStore) {
        this.mergeQueueStore = mergeQueueStore;
    }

    @Override
    public MergeQueueResponse get(final String userId) {
        return mergeQueueStore.getWithVersion(userId)
                .map(queue -> toResponse(queue.value(), queue.version()))
                .orElseGet(() -> new MergeQueueResponse(java.util.List.of(), 0, 0));
    }

    @Override
    public MergeQueueResponse add(final String userId, final AddMergeQueueItemRequest request) {
        return update(userId, queue -> {
            if (queue.items().stream().anyMatch(item -> item.mergeRequestId().equals(request.getMergeRequestId()))) {
                return queue;
            }
            final var items = new ArrayList<>(queue.items());
            items.add(new MergeQueueItem(UUID.randomUUID().toString(), request.getMergeRequestId(), request.getRole(), false));
            return new MergeQueue(queue.id(), items, System.currentTimeMillis());
        });
    }

    @Override
    public MergeQueueResponse remove(final String userId, final String itemId) {
        return update(userId, queue -> new MergeQueue(queue.id(), queue.items().stream()
                .filter(item -> !item.id().equals(itemId))
                .toList(), System.currentTimeMillis()));
    }

    @Override
    public MergeQueueResponse reorder(final String userId, final ReorderMergeQueueRequest request) {
        final var current = mergeQueueStore.getWithVersion(userId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (current.version() != request.getVersion()) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        final var byId = new HashMap<String, MergeQueueItem>();
        current.value().items().forEach(item -> byId.put(item.id(), item));
        if (new HashSet<>(request.getOrderedItemIds()).size() != byId.size()
                || !byId.keySet().equals(new HashSet<>(request.getOrderedItemIds()))) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var reordered = request.getOrderedItemIds().stream().map(byId::get).toList();
        final var next = new MergeQueue(userId, reordered, System.currentTimeMillis());
        try {
            mergeQueueStore.checkAndUpdate(next, current.version());
            return toResponse(next, current.version() + 1);
        } catch (ConcurrentUpdateException conflict) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
    }

    private MergeQueueResponse update(final String queueId, final UnaryOperator<MergeQueue> mutation) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = mergeQueueStore.getWithVersion(queueId);
            if (current.isEmpty()) {
                final var queue = mutation.apply(new MergeQueue(queueId, java.util.List.of(), 0));
                try {
                    mergeQueueStore.create(queue);
                    return toResponse(queue, 0);
                } catch (RuntimeException createRace) {
                    continue;
                }
            }
            final var next = mutation.apply(current.get().value());
            if (next == current.get().value()) {
                return toResponse(next, current.get().version());
            }
            try {
                mergeQueueStore.checkAndUpdate(next, current.get().version());
                return toResponse(next, current.get().version() + 1);
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest ordering.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private MergeQueueResponse toResponse(final MergeQueue queue, final long version) {
        return new MergeQueueResponse(queue.items().stream()
                .map(item -> new MergeQueueItemResponse(item.id(), item.mergeRequestId(), item.role(), item.blocked()))
                .toList(), queue.updatedAt(), version);
    }

}
