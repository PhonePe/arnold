package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.ModelUsage;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.Run;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import com.phonepe.devx.arnold.models.analysis.TokenUsage.Model;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TokenUsageMetricsServiceTest {

    private static TokenUsageMetricsService.Usage usage(final String id, final long createdAt, final TokenUsage usage) {
        return new TokenUsageMetricsService.Usage(new Run(id, "analysis", "gitlab:1:1", "user", "copilot", "completed",
                usage == null ? 0 : usage.inputTokens(), usage == null ? 0 : usage.outputTokens(), createdAt), usage);
    }

    @Test
    void testMerge_shouldSumPerModel_andIgnoreUnreported() {
        final var merged = TokenUsage.merge(Arrays.asList(
                new TokenUsage(10, 1, List.of(new Model("a", 10, 1))), null,
                new TokenUsage(25, 4, List.of(new Model("a", 5, 1), new Model("b", 20, 3)))));
        assertEquals(new TokenUsage(35, 5, List.of(new Model("a", 15, 2), new Model("b", 20, 3))), merged);
        assertNull(TokenUsage.merge(Arrays.asList(null, null)));
    }

    @Test
    void testSummarize_shouldAggregateReportedRuns_newestFirst() {
        final var response = TokenUsageMetricsService.summarize(0, 100, List.of(
                usage("old", 10, new TokenUsage(100, 10, List.of(new Model("a", 100, 10)))),
                usage("none", 20, null),
                usage("new", 30, new TokenUsage(50, 5, List.of(new Model("a", 20, 2), new Model("b", 30, 3))))));

        assertEquals(3, response.runs());
        assertEquals(2, response.reportedRuns());
        assertEquals(150, response.inputTokens());
        assertEquals(15, response.outputTokens());
        assertEquals(List.of(new ModelUsage("a", 2, 120, 12), new ModelUsage("b", 1, 30, 3)), response.models());
        assertEquals(List.of("new", "old"), response.recentRuns().stream().map(Run::runId).toList());
    }

    @Test
    void testMetrics_shouldRejectInvalidRange() {
        final var service = new TokenUsageMetricsService(mock(AerospikeAnalysisRunStore.class),
                mock(AerospikeGroupAnalysisRunStore.class));
        assertThrows(RuntimeException.class, () -> service.metrics(100L, 100L));
        assertThrows(RuntimeException.class, () -> service.metrics(0L, TokenUsageMetricsService.MAX_RANGE_MS + 1));
    }
}
