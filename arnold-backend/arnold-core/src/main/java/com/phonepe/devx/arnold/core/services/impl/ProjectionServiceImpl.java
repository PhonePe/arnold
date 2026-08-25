package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.devx.arnold.core.services.ProjectionService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import com.phonepe.devx.arnold.models.es.CommentDocument;
import com.phonepe.devx.arnold.models.es.MergeRequestDocument;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;

@Slf4j
@Singleton
public class ProjectionServiceImpl implements ProjectionService {
    static final String MERGE_REQUEST_INDEX = "CORE_MERGE_REQUESTS";
    static final String COMMENT_INDEX = "CORE_COMMENTS";
    private static final int MAX_ATTEMPTS = 10;
    private final AerospikeProjectionEventStore eventStore;
    private final AerospikeChangeRequestStore mergeRequestStore;
    private final AerospikeCommentStore commentStore;
    private final ESStorageManager esStorageManager;

    @Inject
    public ProjectionServiceImpl(final AerospikeProjectionEventStore eventStore,
                                 final AerospikeChangeRequestStore mergeRequestStore,
                                 final AerospikeCommentStore commentStore,
                                 final ESStorageManager esStorageManager) {
        this.eventStore = eventStore;
        this.mergeRequestStore = mergeRequestStore;
        this.commentStore = commentStore;
        this.esStorageManager = esStorageManager;
    }

    @Override
    public int processPending(final int limit) {
        return eventStore.getByRefId("status:pending").stream()
                .filter(event -> "pending".equals(event.status()))
                .filter(event -> event.attempts() < MAX_ATTEMPTS)
                .sorted(Comparator.comparingLong(ProjectionEvent::createdAt))
                .limit(limit)
                .mapToInt(this::process)
                .sum();
    }

    private int process(final ProjectionEvent event) {
        try {
            switch (event.aggregateType()) {
                case "merge_request" -> projectMergeRequest(event.aggregateId());
                case "comment" -> projectComment(event.aggregateId());
                default -> throw new IllegalArgumentException("Unknown aggregate type: " + event.aggregateType());
            }
            eventStore.delete(event.id());
            return 1;
        } catch (RuntimeException failure) {
            log.error("Failed to project event id={}", event.id(), failure);
            if (event.attempts() + 1 >= MAX_ATTEMPTS) {
                eventStore.delete(event.id());
            } else {
                eventStore.update(new ProjectionEvent(event.id(), event.aggregateType(), event.aggregateId(),
                        event.operation(), "pending", event.attempts() + 1, event.createdAt(), 0));
            }
            return 0;
        }
    }

    private void projectMergeRequest(final String id) {
        final var mr = mergeRequestStore.get(id).orElseThrow();
        esStorageManager.save(MERGE_REQUEST_INDEX, id, new MergeRequestDocument(mr.id(),
                Long.parseLong(mr.repositoryId()), Long.parseLong(mr.providerId()), mr.number(), mr.repositoryPath(),
                mr.title(), mr.description(), mr.webUrl(), mr.state(), mr.mergeStatus(), mr.draft(), mr.authorId(),
                mr.assigneeIds(), mr.reviewerIds(), mr.labels(), mr.createdAt(), mr.mergedAt(), mr.closedAt(),
                mr.mergedAt() > 0 && mr.createdAt() > 0 ? Math.max(0, mr.mergedAt() - mr.createdAt()) : 0,
                mr.hasConflicts(), mr.blockingDiscussionsResolved(), mr.providerUpdatedAt(), mr.lastSyncedAt()));
    }

    private void projectComment(final String id) {
        final var comment = commentStore.get(id).orElseThrow();
        esStorageManager.save(COMMENT_INDEX, id, new CommentDocument(comment.id(), comment.changeRequestId(),
                comment.discussionId(), comment.noteId(), comment.authorId(), comment.body(), comment.resolved(),
                comment.deleted(), comment.source(), comment.providerUpdatedAt()));
    }
}
