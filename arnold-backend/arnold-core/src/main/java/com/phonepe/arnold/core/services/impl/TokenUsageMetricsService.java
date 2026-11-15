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

import com.phonepe.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.analysis.FlowJobUsage;
import com.phonepe.arnold.core.model.events.FlowJobUsageReported;
import com.phonepe.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore;
import com.phonepe.arnold.models.admin.TokenUsageMetricsResponse;
import com.phonepe.arnold.models.admin.TokenUsageMetricsResponse.FlowUsage;
import com.phonepe.arnold.models.admin.TokenUsageMetricsResponse.Job;
import com.phonepe.arnold.models.admin.TokenUsageMetricsResponse.ModelUsage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static com.phonepe.arnold.core.services.impl.ReviewFeedbackMetricsService.DAY_MS;

/**
 * Ledger of token usage reported by every Clio flow job Arnold triggers, and its admin summary. Ledger entries
 * expire after {@link #MAX_RANGE_MS}; their Elasticsearch projections are kept for long-term analytics.
 */
@Slf4j
@Singleton
public class TokenUsageMetricsService {
    static final long MAX_RANGE_MS = 90 * DAY_MS;
    private static final int RECENT_JOBS = 100;

    private final AerospikeFlowJobUsageStore store;
    private final ProjectionQueue projections;

    @Inject
    public TokenUsageMetricsService(final AerospikeFlowJobUsageStore store, final ProjectionQueue projections,
                                    final ArnoldSignalSubscriber signals) {
        this.store = store;
        this.projections = projections;
        signals.subscribe(FlowJobUsageReported.class, "token-usage-ledger", event -> record(event.job()));
    }

    void record(final FlowJobUsage job) {
        // Usage is a running total per job, so the latest report replaces the previous one.
        try {
            upsert(job);
            projections.enqueueBestEffort(ProjectionQueue.FLOW_JOB_USAGE, job.id(), job.updatedAt());
        } catch (RuntimeException failure) {
            log.warn("Failed to record token usage of flow job {}", job.id(), failure);
        }
    }

    private void upsert(final FlowJobUsage job) {
        try {
            store.create(job);
        } catch (RuntimeException exists) {
            store.update(job);
        }
    }

    public TokenUsageMetricsResponse metrics(final Long from, final Long to) {
        final var end = to == null ? System.currentTimeMillis() : to;
        final var start = from == null ? end - 30 * DAY_MS : from;
        if (start >= end || end - start > MAX_RANGE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("from must be before to and the range must not exceed 90 days").build();
        }
        // ponytail: full scan of a 90-day ledger; switch to a createdAt secondary index if volume grows.
        return summarize(start, end, store.list().stream()
                .filter(job -> job.usage() != null && job.createdAt() >= start && job.createdAt() < end).toList());
    }

    static TokenUsageMetricsResponse summarize(final long from, final long to, final List<FlowJobUsage> jobs) {
        final var flows = new TreeMap<String, long[]>();
        final var models = new TreeMap<String, long[]>();
        jobs.forEach(job -> {
            add(flows, job.flow(), job.usage().inputTokens(), job.usage().outputTokens(),
                    job.usage().cacheReadTokens());
            job.usage().models().forEach(model -> add(models, model.model(), model.inputTokens(),
                    model.outputTokens(), model.cacheReadTokens()));
        });
        return new TokenUsageMetricsResponse(from, to, jobs.size(),
                jobs.stream().mapToLong(job -> job.usage().inputTokens()).sum(),
                jobs.stream().mapToLong(job -> job.usage().outputTokens()).sum(),
                jobs.stream().mapToLong(job -> orZero(job.usage().cacheReadTokens())).sum(),
                flows.entrySet().stream().map(entry -> new FlowUsage(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2], entry.getValue()[3])).toList(),
                models.entrySet().stream().map(entry -> new ModelUsage(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2], entry.getValue()[3])).toList(),
                jobs.stream().sorted(Comparator.comparingLong(FlowJobUsage::createdAt).reversed()).limit(RECENT_JOBS)
                        .map(job -> new Job(job.id(), job.flow(), job.runId(), job.target(), job.invokedBy(),
                                job.harness(), job.status(), job.usage(), job.createdAt(), job.updatedAt()))
                        .toList());
    }

    private static void add(final TreeMap<String, long[]> totals, final String key, final long input,
                            final long output, final Long cacheRead) {
        final var value = totals.computeIfAbsent(key, ignored -> new long[4]);
        value[0]++;
        value[1] += input;
        value[2] += output;
        value[3] += orZero(cacheRead);
    }

    private static long orZero(final Long value) {
        return value == null ? 0 : value;
    }
}
