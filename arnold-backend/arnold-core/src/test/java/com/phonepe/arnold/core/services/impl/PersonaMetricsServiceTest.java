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

import com.phonepe.columbus.search.aggregation.Aggregation;
import com.phonepe.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.columbus.search.aggregation.bucket.TermsAggregation;
import com.phonepe.columbus.storage.models.result.SearchResult;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.models.persona.PersonaMetricsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.phonepe.arnold.core.common.Constants.INDEX_REFERENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class PersonaMetricsServiceTest {
    private static final long MONDAY = 1_767_571_200_000L; // 2026-01-05T00:00:00Z
    private final ESStorageManager storageManager = mock(ESStorageManager.class);
    private final PersonaMetricsService service = new PersonaMetricsService(storageManager);

    @Test
    void testMetrics_shouldCombineRunsFindingsFeedbackAndResolution() {
        stub(SearchIndexReferences.ANALYSIS_RUNS, "runs", Map.of(
                "runs", bucket(4, Map.of("versions", terms(Map.of("netra@2", 3L, "netra@1", 1L, "sai@1", 4L)))),
                "findings", bucket(10, Map.of(
                        "status", terms(Map.of("posted", 8L, "failed", 2L)),
                        "findingVersions", terms(Map.of("2", 7L, "1", 3L)),
                        "weeks", filters(Map.of("week_0", bucket(6, Map.of()), "week_1", bucket(4, Map.of())))))));
        stub(INDEX_REFERENCE, "feedback", Map.of("feedback", bucket(6, Map.of(
                "acceptance", terms(Map.of("applied", 3L, "not_applicable", 2L, "ignored", 1L)),
                "versions", TermsAggregation.builder().name("versions").buckets(List.of(
                        TermsAggregation.Bucket.builder().key(2L).count(6).aggregations(Map.of("acceptance",
                                terms(Map.of("applied", 3L, "not_applicable", 2L, "ignored", 1L)))).build())).build(),
                "weeks", filters(Map.of("week_0", bucket(6, Map.of("acceptance",
                        terms(Map.of("applied", 3L, "not_applicable", 2L, "ignored", 1L))))))))));
        stub(SearchIndexReferences.COMMENTS, "comments", Map.of("comments", bucket(8, Map.of("resolvable",
                filters(Map.of("resolvable", bucket(8, Map.of("resolved",
                        filters(Map.of("resolved", bucket(6, Map.of())))))))))));

        final var response = service.metrics("netra", MONDAY, MONDAY + 14 * ReviewFeedbackMetricsService.DAY_MS);

        assertEquals(4, response.runs());
        assertEquals(new PersonaMetricsResponse.Findings(10, 8, 2), response.findings());
        assertEquals(new PersonaMetricsResponse.Acceptance(6, 3, 4, 75.0), response.acceptance());
        assertEquals(new PersonaMetricsResponse.Resolution(8, 8, 6, 75.0), response.resolution());
        assertEquals(new PersonaMetricsResponse.Week("2026-01-05", 6, 6, 3, 75.0), response.weekly().get(0));
        assertEquals(new PersonaMetricsResponse.Week("2026-01-12", 4, 0, 0, null), response.weekly().get(1));
        assertEquals(List.of(new PersonaMetricsResponse.Version(2, 3, 7, 6, 3, 75.0),
                new PersonaMetricsResponse.Version(1, 1, 3, 0, 0, null)), response.versions());
    }

    @Test
    void testSummary_shouldListPersonasAndGeneralBucket() {
        stub(SearchIndexReferences.ANALYSIS_RUNS, "runs", Map.of(
                "runs", bucket(5, Map.of("personas", terms(Map.of("netra", 3L)))),
                "findings", bucket(12, Map.of(
                        "findingPersonas", TermsAggregation.builder().name("findingPersonas").buckets(List.of(
                                TermsAggregation.Bucket.builder().key("netra").count(4).aggregations(Map.of("posted",
                                        filters(Map.of("posted", bucket(3, Map.of()))))).build())).build(),
                        "general", filters(Map.of("general", bucket(8, Map.of("posted",
                                filters(Map.of("posted", bucket(8, Map.of())))))))))));
        stub(INDEX_REFERENCE, "feedback", Map.of(
                "personas", bucket(2, Map.of("personas", TermsAggregation.builder().name("personas").buckets(List.of(
                        TermsAggregation.Bucket.builder().key("sai").count(2).aggregations(Map.of("acceptance",
                                terms(Map.of("applied", 1L, "ignored", 1L)))).build())).build())),
                "general", bucket(5, Map.of("acceptance", terms(Map.of("applied", 1L, "not_applicable", 1L,
                        "ignored", 3L))))));

        final var response = service.summary(MONDAY, MONDAY + ReviewFeedbackMetricsService.DAY_MS);

        assertEquals(2, response.personas().size());
        assertEquals("netra", response.personas().get(0).personaId());
        assertEquals(3, response.personas().get(0).runs());
        assertEquals(4, response.personas().get(0).findings());
        assertEquals(3, response.personas().get(0).posted());
        assertNull(response.personas().get(0).acceptanceRate());
        assertEquals(50.0, response.personas().get(1).acceptanceRate());
        assertNull(response.general().personaId());
        assertEquals(5, response.general().runs());
        assertEquals(8, response.general().findings());
        assertEquals(25.0, response.general().acceptanceRate());
    }

    @Test
    void testMetrics_shouldRejectInvalidRange() {
        assertThrows(ArnoldError.class, () -> service.metrics("netra", 10L, 5L));
    }

    @Test
    void testSummary_shouldTreatMissingProjectionIndexAsEmpty() {
        doThrow(new IllegalStateException("search failed", new RuntimeException(
                "type=index_not_found_exception, reason=no such index [arnold.analysis.runs]")))
                .when(storageManager).search(eq(SearchIndexReferences.ANALYSIS_RUNS), any(), any(), anyList(),
                        anyList(), anyInt(), anyInt());
        stub(INDEX_REFERENCE, "feedback", Map.of("general", bucket(2, Map.of("acceptance",
                terms(Map.of("applied", 1L, "ignored", 1L))))));

        final var response = service.summary(MONDAY, MONDAY + ReviewFeedbackMetricsService.DAY_MS);

        assertEquals(List.of(), response.personas());
        assertEquals(0, response.general().runs());
        assertEquals(0, response.general().findings());
        assertEquals(50.0, response.general().acceptanceRate());
    }

    @Test
    void testSummary_shouldPropagateOtherSearchFailures() {
        doThrow(new IllegalStateException("cluster unavailable")).when(storageManager)
                .search(any(), any(), any(), anyList(), anyList(), anyInt(), anyInt());

        assertThrows(IllegalStateException.class,
                () -> service.summary(MONDAY, MONDAY + ReviewFeedbackMetricsService.DAY_MS));
    }

    private void stub(final String index, final String name, final Map<String, FiltersAggregation.Bucket> buckets) {
        final var result = SearchResult.builder()
                .aggregations(Map.<String, Aggregation>of(name, filters(buckets)))
                .build();
        doReturn(result).when(storageManager).search(eq(index), any(), any(), anyList(), anyList(), anyInt(),
                anyInt());
    }

    private static FiltersAggregation filters(final Map<String, FiltersAggregation.Bucket> buckets) {
        return FiltersAggregation.builder().name("filters").buckets(buckets).build();
    }

    private static FiltersAggregation.Bucket bucket(final long count, final Map<String, Aggregation> aggregations) {
        return FiltersAggregation.Bucket.builder().count(count).aggregations(aggregations).build();
    }

    private static TermsAggregation terms(final Map<String, Long> counts) {
        return TermsAggregation.builder().name("terms").buckets(counts.entrySet().stream()
                .map(entry -> TermsAggregation.Bucket.builder().key(entry.getKey()).count(entry.getValue()).build())
                .toList()).build();
    }
}
