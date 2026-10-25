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

package com.phonepe.arnold.core.services.impl;

import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.arnold.models.es.MergeRequestDocument;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceImplTest {
    private ESStorageManager storageManager;
    private VersionControlIntegrationService integrationService;
    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        storageManager = mock(ESStorageManager.class);
        integrationService = mock(VersionControlIntegrationService.class);
        service = new StatsServiceImpl(storageManager, integrationService);
    }

    @Test
    void testPersonal_shouldResolveParticipantsAcrossConnectedProviders() {
        when(integrationService.status(VersionControlProviderType.GITLAB, "user-1"))
                .thenReturn(integration("GITLAB", "10"));
        when(integrationService.status(VersionControlProviderType.GITHUB, "user-1"))
                .thenReturn(integration("GITHUB", "20"));
        final SearchResult<MergeRequestDocument> searchResult = SearchResult.<MergeRequestDocument>builder()
                .aggregations(Map.of(
                        "counts", FiltersAggregation.builder().name("counts").buckets(Map.of()).build(),
                        "data_through", StatsAggregation.builder().name("data_through").count(0).build()))
                .build();
        doReturn(searchResult).when(storageManager)
                .search(anyString(), any(), any(), anyList(), anyList(), anyInt(), anyInt());

        final var response = service.personal("user-1");

        assertEquals("personal", response.scope());
        verify(integrationService).status(VersionControlProviderType.GITLAB, "user-1");
        verify(integrationService).status(VersionControlProviderType.GITHUB, "user-1");
        verify(storageManager).search(eq(SearchIndexReferences.CHANGE_REQUESTS), any(), any(), anyList(), anyList(),
                anyInt(), anyInt());
    }

    @Test
    void testOrganization_shouldUseServerSideAggregationsWithoutLoadingDocuments() {
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
        verify(storageManager).search(eq(SearchIndexReferences.CHANGE_REQUESTS), any(), any(), anyList(), anyList(),
                anyInt(), anyInt());
    }

    @Test
    void testOrganization_shouldExcludeArchivedProjectsFromScope() {
        final SearchResult<MergeRequestDocument> searchResult = SearchResult.<MergeRequestDocument>builder()
                .aggregations(Map.of(
                        "counts", FiltersAggregation.builder().name("counts").buckets(Map.of()).build(),
                        "data_through", StatsAggregation.builder().name("data_through").count(0).build()))
                .build();
        doReturn(searchResult).when(storageManager)
                .search(anyString(), any(), any(), anyList(), anyList(), anyInt(), anyInt());

        service.organization();

        final ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(storageManager).search(eq(SearchIndexReferences.CHANGE_REQUESTS), queryCaptor.capture(), any(),
                anyList(), anyList(), anyInt(), anyInt());
        assertTrue(excludesArchived(queryCaptor.getValue()),
                "Stats scope query must exclude archived projects");
    }

    private boolean excludesArchived(final Query query) {
        final Deque<Query> pending = new ArrayDeque<>();
        pending.add(query);
        while (!pending.isEmpty()) {
            final var current = pending.poll();
            if (current instanceof BoolQuery bool) {
                for (final var clause : bool.getMustNotClauses()) {
                    if (clause instanceof TermQuery term && "projectArchived".equals(term.getField())
                            && Boolean.TRUE.equals(term.getValue())) {
                        return true;
                    }
                }
                pending.addAll(bool.getMustClauses());
                pending.addAll(bool.getShouldClauses());
                pending.addAll(bool.getFilterClauses());
                pending.addAll(bool.getMustNotClauses());
            }
        }
        return false;
    }

    private FiltersAggregation.Bucket bucket(final long count) {
        return FiltersAggregation.Bucket.builder().count(count).build();
    }

    private VersionControlIntegrationResponse integration(final String provider, final String providerUserId) {
        return new VersionControlIntegrationResponse(true, provider, "OAUTH", providerUserId, "user",
                Set.of(), null, "connected");
    }
}
