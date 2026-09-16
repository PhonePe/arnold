package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.TransitionEvent;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProjectionEventStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class VersionControlSyncServiceImpl implements VersionControlSyncService {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

    private final ChangeRequestService changeRequestService;
    private final AerospikeCommentStore commentStore;
    private final AerospikeProjectionEventStore projectionEventStore;
    private final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine;

    @Inject
    public VersionControlSyncServiceImpl(
            final ChangeRequestService changeRequestService,
            final AerospikeCommentStore commentStore,
            final AerospikeProjectionEventStore projectionEventStore,
            final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> stateMachine) {
        this.changeRequestService = changeRequestService;
        this.commentStore = commentStore;
        this.projectionEventStore = projectionEventStore;
        this.stateMachine = stateMachine;
    }

    @Override
    public boolean synchronize(final ChangeRequestSync sync) {
        final var changeRequest = sync.changeRequest();
        if (!changeRequestService.synchronize(changeRequest)) {
            return false;
        }
        stateMachine.initialize(changeRequest.id(), StateInfo.of(ReviewState.CREATED, ReviewStateData.initial()));
        switch (sync.action()) {
            case MERGED -> stateMachine.tryFire(changeRequest.id(), TransitionEvent.of(ReviewEvent.CHANGE_MERGED,
                    new ReviewEventPayload.ChangeMerged(changeRequest.headSha())));
            case CLOSED -> stateMachine.tryFire(changeRequest.id(), TransitionEvent.of(ReviewEvent.CHANGE_CLOSED,
                    new ReviewEventPayload.ChangeClosed(sync.actor())));
            case REOPENED -> stateMachine.tryFire(changeRequest.id(), TransitionEvent.of(ReviewEvent.CHANGE_REOPENED,
                    new ReviewEventPayload.ChangeReopened(sync.actor())));
            case UPSERT -> { }
        }
        enqueueProjection("merge_request", changeRequest.id(), changeRequest.providerUpdatedAt());
        return true;
    }

    @Override
    public void synchronize(final CommentRecord comment) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = commentStore.getWithVersion(comment.id());
            if (current.isEmpty()) {
                try {
                    commentStore.create(comment);
                    enqueueProjection("comment", comment.id(), comment.providerUpdatedAt());
                    return;
                } catch (RuntimeException createRace) {
                    continue;
                }
            }
            if (current.get().value().providerUpdatedAt() > comment.providerUpdatedAt()) {
                return;
            }
            try {
                commentStore.checkAndUpdate(comment, current.get().version());
                enqueueProjection("comment", comment.id(), comment.providerUpdatedAt());
                return;
            } catch (RuntimeException conflict) {
                // Retry against the latest datastore generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private void enqueueProjection(final String aggregateType, final String aggregateId, final long sourceUpdatedAt) {
        final var id = aggregateType + ":" + aggregateId + ":" + sourceUpdatedAt;
        if (projectionEventStore.get(id).isEmpty()) {
            try {
                projectionEventStore.create(new ProjectionEvent(id, aggregateType, aggregateId, "upsert", "pending",
                        0, System.currentTimeMillis(), 0), java.util.List.of("status:pending"));
            } catch (RuntimeException ignoredRace) {
                // Deterministic IDs make duplicate projection creation harmless.
            }
        }
    }
}
