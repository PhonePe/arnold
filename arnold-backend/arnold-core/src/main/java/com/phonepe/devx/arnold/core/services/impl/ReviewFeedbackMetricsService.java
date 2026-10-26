package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.commons.columbus.search.aggregation.Aggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.TermsAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.search.aggregator.Aggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.TermsAggregator;
import com.phonepe.commons.columbus.search.aggregator.metrics.StatsAggregator;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Origin;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Score;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Week;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.phonepe.devx.arnold.core.common.Constants.INDEX_REFERENCE;

/**
 * Aggregates review-feedback outcomes from the {@code CORE_REVIEWS} index in a single search, bucketed by origin.
 * Feedback is attributed to Arnold when {@code origin=arnold} or it carries an {@code analysisRunId}; the latter
 * covers documents ingested before {@code origin} existed.
 */
@Singleton
public class ReviewFeedbackMetricsService {
    static final long DAY_MS = 24L * 60 * 60 * 1000;
    static final long WEEK_MS = 7 * DAY_MS;
    static final long MAX_RANGE_MS = 366 * DAY_MS;
    // ponytail: weekly buckets capped to the most recent 27; switch to monthly buckets if longer trends matter.
    static final int MAX_WEEKS = 27;
    private static final int TOP_N = 10;
    private static final String ORIGIN = "origin";
    private static final String ARNOLD = ReviewFeedbackDocument.ORIGIN_ARNOLD;
    private static final String WORKFLOW = ReviewFeedbackDocument.ORIGIN_WORKFLOW;
    private static final String ACCEPTANCE = "acceptance";
    private static final String SCORE = "score";
    private static final String WEEKS = "weeks";
    private static final Map<String, String> DIMENSIONS = dimensions();

    private final ESStorageManager storageManager;

    @Inject
    public ReviewFeedbackMetricsService(final ESStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public ReviewFeedbackMetricsResponse metrics(final Long from, final Long to, final Long projectId,
                                                 final String model, final String personaId) {
        final var end = to == null ? System.currentTimeMillis() : to;
        final var start = from == null ? end - 30 * DAY_MS : from;
        if (start >= end || end - start > MAX_RANGE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("from must be before to and the range must not exceed 366 days").build();
        }
        final var filters = new ArrayList<Query>();
        filters.add(RangeQuery.builder().field("ingestedAt").from(start).to(end).includeUpper(false).build());
        if (projectId != null) {
            filters.add(term("project.projectId", projectId));
        }
        if (model != null && !model.isBlank()) {
            filters.add(term("sourceModel.keyword", model));
        }
        if (personaId != null && !personaId.isBlank()) {
            filters.add(term("personaId.keyword", personaId));
        }
        final var weeks = weekStarts(start, end);
        final var byOrigin = FiltersAggregator.builder().name(ORIGIN)
                .filters(originFilters())
                .subAggregators(subAggregators(weeks, end))
                .build();
        final var search = storageManager.search(INDEX_REFERENCE, BoolQuery.builder().filterClauses(filters).build(),
                new TypeReference<ReviewFeedbackDocument>() { }, List.of(), List.<Aggregator>of(byOrigin), 0, 0);
        final var origins = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(ORIGIN);
        final var buckets = origins == null || origins.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : origins.getBuckets();
        return new ReviewFeedbackMetricsResponse(start, end, origin(buckets.get(ARNOLD), weeks),
                origin(buckets.get(WORKFLOW), weeks));
    }

    private static Map<String, Query> originFilters() {
        final var arnold = BoolQuery.builder().shouldClauses(List.of(term("origin.keyword", ARNOLD),
                FieldExistsQuery.builder().field("analysisRunId").build())).minimumMatch("1").build();
        final var filters = new LinkedHashMap<String, Query>();
        filters.put(ARNOLD, arnold);
        filters.put(WORKFLOW, BoolQuery.builder().mustNot(arnold).build());
        return filters;
    }

    private static List<Aggregator> subAggregators(final List<Long> weeks, final long end) {
        final var aggregators = new ArrayList<Aggregator>();
        aggregators.add(terms(ACCEPTANCE, "acceptanceStatus.keyword", TOP_N));
        DIMENSIONS.forEach((name, field) -> aggregators.add(terms(name, field, TOP_N)));
        aggregators.add(StatsAggregator.builder().name(SCORE).field("score").build());
        final var weekFilters = new LinkedHashMap<String, Query>();
        for (var index = 0; index < weeks.size(); index++) {
            final var weekEnd = index + 1 < weeks.size() ? weeks.get(index + 1) : end;
            weekFilters.put(weekKey(index), RangeQuery.builder().field("ingestedAt").from(weeks.get(index))
                    .to(weekEnd).includeUpper(false).build());
        }
        aggregators.add(FiltersAggregator.builder().name(WEEKS).filters(weekFilters)
                .subAggregator(terms(ACCEPTANCE, "acceptanceStatus.keyword", TOP_N)).build());
        return aggregators;
    }

    private static Origin origin(final FiltersAggregation.Bucket bucket, final List<Long> weeks) {
        final var aggregations = bucket == null || bucket.getAggregations() == null
                ? Map.<String, Aggregation>of() : bucket.getAggregations();
        final var total = bucket == null ? 0 : bucket.getCount();
        final var acceptance = counts(aggregations.get(ACCEPTANCE));
        final var breakdowns = new LinkedHashMap<String, Map<String, Long>>();
        DIMENSIONS.keySet().forEach(name -> breakdowns.put(name, counts(aggregations.get(name))));
        final var accepted = accepted(acceptance);
        final var actionable = total - acceptance.getOrDefault("not_applicable", 0L);
        return new Origin(total, accepted, actionable, rate(accepted, actionable),
                score((StatsAggregation) aggregations.get(SCORE)), acceptance, breakdowns.get("priority"),
                breakdowns.get("commentType"), suggestion(breakdowns.get("suggestion")), breakdowns.get("category"),
                breakdowns.get("model"), breakdowns.get("persona"), breakdowns.get("project"),
                weekly((FiltersAggregation) aggregations.get(WEEKS), weeks));
    }

    private static List<Week> weekly(final FiltersAggregation aggregation, final List<Long> weeks) {
        final var buckets = aggregation == null || aggregation.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : aggregation.getBuckets();
        final var result = new ArrayList<Week>();
        for (var index = 0; index < weeks.size(); index++) {
            final var bucket = buckets.get(weekKey(index));
            final var total = bucket == null ? 0 : bucket.getCount();
            final var acceptance = bucket == null || bucket.getAggregations() == null ? Map.<String, Long>of()
                    : counts(bucket.getAggregations().get(ACCEPTANCE));
            final var accepted = accepted(acceptance);
            result.add(new Week(Instant.ofEpochMilli(weeks.get(index)).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    total, accepted, rate(accepted, total - acceptance.getOrDefault("not_applicable", 0L))));
        }
        return List.copyOf(result);
    }

    static List<Long> weekStarts(final long start, final long end) {
        final var weeks = new ArrayList<Long>();
        var cursor = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        for (; cursor < end; cursor += WEEK_MS) {
            weeks.add(Math.max(cursor, start));
        }
        return List.copyOf(weeks.subList(Math.max(0, weeks.size() - MAX_WEEKS), weeks.size()));
    }

    private static Map<String, Long> counts(final Aggregation aggregation) {
        final var counts = new LinkedHashMap<String, Long>();
        if (aggregation instanceof TermsAggregation terms && terms.getBuckets() != null) {
            terms.getBuckets().forEach(bucket -> counts.put(String.valueOf(bucket.getKey()), bucket.getCount()));
        }
        return counts;
    }

    private static Map<String, Long> suggestion(final Map<String, Long> raw) {
        final var counts = new LinkedHashMap<String, Long>();
        raw.forEach((key, count) -> counts.merge("1".equals(key) || "true".equalsIgnoreCase(key)
                ? "with_suggestion" : "without_suggestion", count, Long::sum));
        return counts;
    }

    private static long accepted(final Map<String, Long> acceptance) {
        return acceptance.getOrDefault("applied", 0L) + acceptance.getOrDefault("partially_applied", 0L);
    }

    private static Double rate(final long accepted, final long actionable) {
        return actionable <= 0 ? null : accepted * 100.0 / actionable;
    }

    private static Score score(final StatsAggregation stats) {
        return stats == null || stats.getCount() == 0 ? new Score(0, null, null, null)
                : new Score(stats.getCount(), stats.getAverage(), stats.getMinimum(), stats.getMaximum());
    }

    private static Aggregator terms(final String name, final String field, final int size) {
        return TermsAggregator.builder().name(name).field(field).size(size).build();
    }

    private static Query term(final String field, final Object value) {
        return TermQuery.builder().field(field).value(value).build();
    }

    private static String weekKey(final int index) {
        return "week_" + index;
    }

    private static Map<String, String> dimensions() {
        final var dimensions = new LinkedHashMap<String, String>();
        dimensions.put("priority", "priority.keyword");
        dimensions.put("commentType", "commentType.keyword");
        dimensions.put("suggestion", "hasSuggestion");
        dimensions.put("category", "commentCategory.keyword");
        dimensions.put("model", "sourceModel.keyword");
        dimensions.put("persona", "personaId.keyword");
        dimensions.put("project", "project.projectName.keyword");
        return Map.copyOf(dimensions);
    }
}
