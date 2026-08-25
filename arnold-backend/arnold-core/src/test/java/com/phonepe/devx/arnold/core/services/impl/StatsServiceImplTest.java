package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.models.es.MergeRequestDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatsServiceImplTest {
    private ESStorageManager storageManager;
    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        storageManager = mock(ESStorageManager.class);
        service = new StatsServiceImpl(storageManager, mock(GitLabOAuthService.class));
    }

    @Test
    void organization_shouldUseServerSideAggregationsWithoutLoadingDocuments() {
        final var buckets = new LinkedHashMap<String, FiltersAggregation.Bucket>();
        buckets.put("open_authored", bucket(7));
        buckets.put("open_review_work", bucket(4));
        buckets.put("merged", bucket(0));
        buckets.put("current_throughput", bucket(5));
        buckets.put("previous_throughput", bucket(0));
        for (var week = 0; week < 6; week++) {
            buckets.put("week_" + week + "_authored", bucket(week + 1));
            buckets.put("week_" + week + "_reviewed", bucket(week + 2));
        }
        final SearchResult<MergeRequestDocument> searchResult = SearchResult.<MergeRequestDocument>builder()
                .aggregations(Map.of(
                        "counts", FiltersAggregation.builder().name("counts").buckets(buckets).build(),
                        "data_through", StatsAggregation.builder().name("data_through").count(3)
                                .maximum(1234).build()))
                .build();
        doReturn(searchResult).when(storageManager)
                .search(anyString(), any(), any(), anyList(), anyList(), anyInt(), anyInt());

        final var response = service.organization();

        assertEquals("organization", response.scope());
        assertEquals(7, response.openAuthored());
        assertEquals(4, response.openReviewWork());
        assertEquals(0, response.merged());
        assertEquals(0, response.medianTimeToMergeMs());
        assertNull(response.throughputDeltaPct());
        assertEquals(6, response.weekly().size());
        assertEquals(1, response.weekly().get(0).authoredMerged());
        assertEquals(2, response.weekly().get(0).reviewWorkCompleted());
        assertEquals(1234, response.dataThrough());
        verify(storageManager).search(anyString(), any(), any(), anyList(), anyList(), anyInt(), anyInt());
    }

    private FiltersAggregation.Bucket bucket(final long count) {
        return FiltersAggregation.Bucket.builder().count(count).build();
    }
}
