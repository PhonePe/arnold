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

import com.phonepe.arnold.models.es.BackfillResult;
import com.phonepe.arnold.models.request.BackfillRequest;

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
