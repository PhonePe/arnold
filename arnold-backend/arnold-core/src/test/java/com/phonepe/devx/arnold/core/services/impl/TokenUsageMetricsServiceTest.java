package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldSignals;
import com.phonepe.devx.arnold.core.model.analysis.FlowJobUsage;
import com.phonepe.devx.arnold.core.model.events.FlowJobUsageReported;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeFlowJobUsageStore;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.FlowUsage;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.Job;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.ModelUsage;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import com.phonepe.devx.arnold.models.analysis.TokenUsage.Model;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenUsageMetricsServiceTest {

    private static FlowJobUsage job(final String id, final String flow, final long createdAt, final TokenUsage usage) {
        return new FlowJobUsage(flow + ":" + id, flow, id, "gitlab:1:1", "user", "copilot", "completed", usage,
                createdAt, createdAt);
    }

    @Test
    void testMerge_shouldSumPerModel_andKeepJobUrlOnlyForSingleUsage() {
        final var single = new TokenUsage(10, 1, List.of(new Model("a", 10, 1)), "https://gitlab/jobs/1");
        assertEquals(single, TokenUsage.merge(Arrays.asList(single, null)));
        final var merged = TokenUsage.merge(List.of(single,
                new TokenUsage(25, 4, List.of(new Model("a", 5, 1), new Model("b", 20, 3)), "https://gitlab/jobs/2")));
        assertEquals(new TokenUsage(35, 5, List.of(new Model("a", 15, 2), new Model("b", 20, 3))), merged);
        assertNull(TokenUsage.merge(Arrays.asList(null, null)));
    }

    @Test
    void testSummarize_shouldAggregatePerFlowAndModel_newestFirst() {
        final var response = TokenUsageMetricsService.summarize(0, 100, List.of(
                job("old", "improve", 10, new TokenUsage(100, 10, List.of(new Model("a", 100, 10)))),
                job("diff", "smart_diff", 20, new TokenUsage(7, 1, List.of(new Model("b", 7, 1)))),
                job("new", "improve", 30, new TokenUsage(50, 5, List.of(new Model("a", 20, 2), new Model("b", 30, 3))))));

        assertEquals(3, response.jobs());
        assertEquals(157, response.inputTokens());
        assertEquals(16, response.outputTokens());
        assertEquals(List.of(new FlowUsage("improve", 2, 150, 15), new FlowUsage("smart_diff", 1, 7, 1)),
                response.flows());
        assertEquals(List.of(new ModelUsage("a", 2, 120, 12), new ModelUsage("b", 2, 37, 4)), response.models());
        assertEquals(List.of("new", "diff", "old"), response.recentJobs().stream().map(Job::runId).toList());
    }

    @Test
    void testReported_shouldReplacePreviousUsage_ofSameJob() {
        final var store = mock(AerospikeFlowJobUsageStore.class);
        final var projections = mock(ProjectionQueue.class);
        final var signals = new ArnoldSignals();
        new TokenUsageMetricsService(store, projections, signals);
        final var job = job("run", "group_review", 1, new TokenUsage(1, 1, List.of()));
        doThrow(new RuntimeException("exists")).when(store).create(any(FlowJobUsage.class));

        signals.publish(new FlowJobUsageReported(job));

        verify(store).update(job);
        verify(projections).enqueueBestEffort(ProjectionQueue.FLOW_JOB_USAGE, job.id(), job.updatedAt());
    }

    @Test
    void testMetrics_shouldRejectInvalidRange() {
        final var service = new TokenUsageMetricsService(mock(AerospikeFlowJobUsageStore.class),
                mock(ProjectionQueue.class), new ArnoldSignals());
        assertThrows(RuntimeException.class, () -> service.metrics(100L, 100L));
        assertThrows(RuntimeException.class, () -> service.metrics(0L, TokenUsageMetricsService.MAX_RANGE_MS + 1));
    }
}
