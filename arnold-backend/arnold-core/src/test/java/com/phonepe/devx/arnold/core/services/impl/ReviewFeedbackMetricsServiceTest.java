package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.commons.columbus.search.aggregation.Aggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.TermsAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.CardinalityAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.search.aggregator.Aggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.storage.models.result.SearchResult;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.models.es.CommentDocument;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewFeedbackMetricsServiceTest {
    private static final long MONDAY = 1_767_571_200_000L; // 2026-01-05T00:00:00Z
    private ESStorageManager storageManager;
    private ReviewFeedbackMetricsService service;

    @BeforeEach
    void setUp() {
        storageManager = mock(ESStorageManager.class);
        service = new ReviewFeedbackMetricsService(storageManager);
    }

    @Test
    void testMetrics_shouldSplitByOriginAndComputeAcceptance() {
        final var arnold = bucket(10, Map.of(
                "acceptance", terms("acceptance", Map.of("applied", 5L, "partially_applied", 1L,
                        "ignored", 2L, "not_applicable", 2L)),
                "suggestion", terms("suggestion", Map.of("1", 4L, "0", 6L)),
                "model", terms("model", Map.of("gpt", 10L)),
                "score", StatsAggregation.builder().name("score").count(10).average(0.8).minimum(0.1).maximum(1.0)
                        .build(),
                "weeks", FiltersAggregation.builder().name("weeks").buckets(Map.of(
                        "week_0", bucket(4, Map.of("acceptance", terms("acceptance", Map.of("applied", 2L)))),
                        "week_1", bucket(6, Map.of()))).build()));
        final var origins = new LinkedHashMap<String, FiltersAggregation.Bucket>();
        origins.put("arnold", arnold);
        origins.put("workflow", bucket(3, Map.of("acceptance", terms("acceptance", Map.of("ignored", 3L)))));
        origins.put("persona", bucket(4, Map.of("acceptance", terms("acceptance", Map.of("applied", 3L,
                "ignored", 1L)))));
        stub(origins);

        final var response = service.metrics(MONDAY, MONDAY + 14 * ReviewFeedbackMetricsService.DAY_MS, Map.of());

        assertEquals(10, response.arnold().total());
        assertEquals(6, response.arnold().accepted());
        assertEquals(8, response.arnold().actionable());
        assertEquals(75.0, response.arnold().acceptanceRate());
        assertEquals(0.8, response.arnold().score().average());
        assertEquals(Map.of("with_suggestion", 4L, "without_suggestion", 6L), response.arnold().suggestion());
        assertEquals(Map.of("gpt", 10L), response.arnold().model());
        assertEquals(2, response.arnold().weekly().size());
        assertEquals("2026-01-05", response.arnold().weekly().get(0).weekStart());
        assertEquals(50.0, response.arnold().weekly().get(0).acceptanceRate());
        assertEquals(0.0, response.arnold().weekly().get(1).acceptanceRate());
        assertEquals(3, response.workflow().total());
        assertEquals(0.0, response.workflow().acceptanceRate());
        assertNull(response.workflow().score().average());
        assertEquals(75.0, response.personaSplit().persona().acceptanceRate());
        assertEquals(0, response.personaSplit().general().total());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMetrics_shouldRequestOriginFiltersWithSubAggregations() {
        stub(Map.of());

        final var response = service.metrics(null, null,
                Map.of("project", "arnold", "status", "applied", "hasSuggestion", true, "unknown", "x", "model", " "));

        final ArgumentCaptor<List<Aggregator>> captor = ArgumentCaptor.forClass(List.class);
        final var query = ArgumentCaptor.forClass(Query.class);
        verify(storageManager).search(eq("CORE_REVIEWS"), query.capture(), any(), anyList(), captor.capture(), eq(0),
                eq(0));
        final var clauses = ((BoolQuery) query.getValue()).getFilterClauses();
        assertEquals(6, clauses.size());
        assertTrue(clauses.stream().anyMatch(clause -> clause instanceof com.phonepe.commons.columbus.search.query.term.TermsQuery terms
                && "mrId".equals(terms.getField())));
        assertTrue(clauses.contains(TermQuery.builder().field("acceptanceStatus.keyword").value("applied").build()));
        assertTrue(clauses.contains(TermQuery.builder().field("hasSuggestion").value(true).build()));
        assertTrue(clauses.stream().anyMatch(clause -> clause instanceof RangeQuery range
                && "noteCreatedAt".equals(range.getField())));
        final var mergedQuery = ArgumentCaptor.forClass(Query.class);
        verify(storageManager).searchEntities(eq(SearchIndexReferences.CHANGE_REQUESTS), mergedQuery.capture(), any(),
                anyList(), anyInt(), anyInt());
        final var mergedAt = (RangeQuery) ((BoolQuery) mergedQuery.getValue()).getFilterClauses().get(0);
        assertEquals("mergedAt", mergedAt.getField());
        assertEquals(response.from(), mergedAt.getFrom());
        assertNull(mergedAt.getTo());
        final var origin = (FiltersAggregator) captor.getValue().get(0);
        assertEquals(List.of("arnold", "workflow", "ai", "human", "persona", "general"), List.copyOf(origin.getFilters().keySet()));
        final var workflow = (BoolQuery) origin.getFilters().get("workflow");
        assertEquals(List.of(TermQuery.builder().field("reviewer.username.keyword").value("cibot").build()),
                workflow.getFilterClauses());
        assertEquals(List.of(origin.getFilters().get("arnold")), workflow.getMustNotClauses());
        assertEquals(30 * ReviewFeedbackMetricsService.DAY_MS, response.to() - response.from());
        assertEquals(0, response.arnold().total());
        assertNull(response.arnold().acceptanceRate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMetrics_shouldSplitCommentsIntoAiAndHuman() {
        stub(Map.of());
        final var ai = bucket(8, Map.of(
                "resolvable", FiltersAggregation.builder().name("resolvable").buckets(Map.of("resolvable",
                        bucket(6, Map.of("resolved", FiltersAggregation.builder().name("resolved")
                                .buckets(Map.of("resolved", bucket(3, Map.of()))).build())))).build(),
                "mergeRequests", CardinalityAggregation.builder().name("mergeRequests").count(4).build(),
                "weeks", FiltersAggregation.builder().name("weeks").buckets(Map.of("week_1", bucket(8, Map.of())))
                        .build()));
        final var human = bucket(5, Map.of(
                "byMergeRequestAuthor", FiltersAggregation.builder().name("byMergeRequestAuthor")
                        .buckets(Map.of("byMergeRequestAuthor", bucket(2, Map.of()))).build()));
        final SearchResult<CommentDocument> comments = SearchResult.<CommentDocument>builder()
                .aggregations(Map.of("author", FiltersAggregation.builder().name("author")
                        .buckets(Map.of("ai", ai, "human", human)).build()))
                .build();
        doReturn(comments).when(storageManager)
                .search(eq("CORE_COMMENTS"), any(), any(), anyList(), anyList(), anyInt(), anyInt());

        final var authorship = service.metrics(MONDAY, MONDAY + 14 * ReviewFeedbackMetricsService.DAY_MS,
                Map.of("project", "ignored-for-comments")).authorship();

        final ArgumentCaptor<List<Aggregator>> captor = ArgumentCaptor.forClass(List.class);
        verify(storageManager).search(eq("CORE_COMMENTS"), any(), any(), anyList(), captor.capture(), eq(0), eq(0));
        final var author = (FiltersAggregator) captor.getValue().get(0);
        assertEquals(List.of("ai", "human"), List.copyOf(author.getFilters().keySet()));
        assertEquals("cibot", authorship.aiAuthor());
        assertEquals(8, authorship.ai().total());
        assertEquals(50.0, authorship.ai().resolutionRate());
        assertEquals(4, authorship.ai().mergeRequests());
        assertEquals(List.of(0L, 8L), authorship.ai().weekly().stream().map(week -> week.total()).toList());
        assertEquals(5, authorship.human().total());
        assertEquals(2, authorship.human().byMergeRequestAuthor());
        assertNull(authorship.human().resolutionRate());
    }

    @Test
    void testMetrics_shouldRejectInvalidRange() {
        assertThrows(ArnoldError.class, () -> service.metrics(10L, 5L, Map.of()));
        assertThrows(ArnoldError.class, () -> service.metrics(0L, ReviewFeedbackMetricsService.MAX_RANGE_MS + 1,
                Map.of()));
    }

    @Test
    void testWeekStarts_shouldAlignToMondayAndCapBuckets() {
        final var wednesday = MONDAY + 2 * ReviewFeedbackMetricsService.DAY_MS;

        final var weeks = ReviewFeedbackMetricsService.weekStarts(wednesday,
                MONDAY + 2 * ReviewFeedbackMetricsService.WEEK_MS);
        final var capped = ReviewFeedbackMetricsService.weekStarts(MONDAY,
                MONDAY + 52 * ReviewFeedbackMetricsService.WEEK_MS);

        assertEquals(List.of(wednesday, MONDAY + ReviewFeedbackMetricsService.WEEK_MS), weeks);
        assertEquals(ReviewFeedbackMetricsService.MAX_WEEKS, capped.size());
        assertEquals(MONDAY + 51 * ReviewFeedbackMetricsService.WEEK_MS, capped.get(capped.size() - 1));
    }

    private void stub(final Map<String, FiltersAggregation.Bucket> origins) {
        final SearchResult<ReviewFeedbackDocument> result = SearchResult.<ReviewFeedbackDocument>builder()
                .aggregations(Map.of("origin", FiltersAggregation.builder().name("origin").buckets(origins).build()))
                .build();
        doReturn(result).when(storageManager).search(anyString(), any(), any(), anyList(), anyList(), anyInt(), anyInt());
    }

    private static FiltersAggregation.Bucket bucket(final long count, final Map<String, Aggregation> aggregations) {
        return FiltersAggregation.Bucket.builder().count(count).aggregations(aggregations).build();
    }

    private static TermsAggregation terms(final String name, final Map<String, Long> counts) {
        return TermsAggregation.builder().name(name).buckets(counts.entrySet().stream()
                .map(entry -> TermsAggregation.Bucket.builder().key(entry.getKey()).count(entry.getValue()).build())
                .toList()).build();
    }
}
