package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.es.BackfillResult;
import com.phonepe.devx.arnold.models.request.BackfillRequest;

/**
 * Operational / maintenance tasks for the review-feedback data set.
 */
public interface HousekeepingService {

    /**
     * Re-enrich review-feedback documents persisted with missing SCM metadata enrichment
     * (null noteCreatedAt, and/or missing project / MR-author). Idempotent and resumable.
     */
    BackfillResult backfillReviewFeedback(final BackfillRequest request);

    /**
     * Migrate legacy per-user merge-request group collections into per-group records.
     * Idempotent: skips groups that already exist in the new store.
     */
    int migrateMergeRequestGroups();
}
