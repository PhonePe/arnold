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

import com.phonepe.arnold.core.model.projection.ProjectionEvent;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Slf4j
@Singleton
public class ProjectionQueue {
    public static final String MERGE_REQUEST = "merge_request";
    public static final String COMMENT = "comment";
    public static final String ANALYSIS_RUN = "analysis_run";
    public static final String GROUP_ANALYSIS_RUN = "group_analysis_run";
    public static final String SMART_DIFF = "smart_diff";
    public static final String REVIEW_PROGRESS = "review_progress";
    public static final String FLOW_JOB_USAGE = "flow_job_usage";

    private final AerospikeProjectionEventStore store;

    @Inject
    public ProjectionQueue(final AerospikeProjectionEventStore store) {
        this.store = store;
    }

    public void enqueue(final String aggregateType, final String aggregateId, final long sourceUpdatedAt) {
        final var id = aggregateType + ":" + aggregateId + ":" + sourceUpdatedAt;
        if (store.get(id).isEmpty()) {
            try {
                store.create(new ProjectionEvent(id, aggregateType, aggregateId, "upsert", "pending",
                        0, System.currentTimeMillis(), 0), List.of("status:pending"));
            } catch (RuntimeException ignoredRace) {
                // Deterministic IDs make duplicate projection creation harmless.
            }
        }
    }

    /**
     * Analytics-only projections must never fail the business write that triggered them.
     */
    public void enqueueBestEffort(final String aggregateType, final String aggregateId, final long sourceUpdatedAt) {
        try {
            enqueue(aggregateType, aggregateId, sourceUpdatedAt);
        } catch (RuntimeException failure) {
            log.warn("Failed to enqueue projection type={} id={}", aggregateType, aggregateId, failure);
        }
    }
}
