package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.commons.columbus.search.aggregation.Aggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.bucket.TermsAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.CardinalityAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.search.aggregator.Aggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.TermsAggregator;
import com.phonepe.commons.columbus.search.aggregator.metrics.CardinalityAggregator;
import com.phonepe.commons.columbus.search.aggregator.metrics.StatsAggregator;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Authors;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Authorship;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.CommentWeek;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Origin;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Score;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse.Week;
import com.phonepe.devx.arnold.models.es.CommentDocument;
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
 * covers documents ingested before {@code origin} existed. A second search over {@code CORE_COMMENTS} splits all
 * published MR comments into AI (authored by {@link #AI_AUTHOR}) and human.
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
    static final Map<String, String> FILTER_FIELDS = Map.of(
            "project", "project.projectName.keyword",
            "model", "sourceModel.keyword",
            "personaId", "personaId.keyword",
            "status", "acceptanceStatus.keyword",
            "priority", "priority.keyword",
            "commentType", "commentType.keyword",
            "category", "commentCategory.keyword",
            "hasSuggestion", "hasSuggestion");
    static final String AI_AUTHOR = "cibot";
    private static final String AI = "ai";
    private static final String HUMAN = "human";
    private static final String AUTHOR = "author";
    private static final String RESOLVABLE = "resolvable";
    private static final String RESOLVED = "resolved";
    private static final String MERGE_REQUESTS = "mergeRequests";
    private static final String BY_MR_AUTHOR = "byMergeRequestAuthor";

    private final ESStorageManager storageManager;

    @Inject
    public ReviewFeedbackMetricsService(final ESStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    /**
     * @param filters query-param name (see {@link #FILTER_FIELDS}) to the exact value to match; blank values and
     *                unknown names are ignored. They apply to review feedback only, not to comment authorship.
     */
    public ReviewFeedbackMetricsResponse metrics(final Long from, final Long to, final Map<String, ?> filters) {
        final var end = to == null ? System.currentTimeMillis() : to;
        final var start = from == null ? end - 30 * DAY_MS : from;
        if (start >= end || end - start > MAX_RANGE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("from must be before to and the range must not exceed 366 days").build();
        }
        final var clauses = new ArrayList<Query>();
        clauses.add(RangeQuery.builder().field("ingestedAt").from(start).to(end).includeUpper(false).build());
        FILTER_FIELDS.forEach((name, field) -> {
            final var value = filters == null ? null : filters.get(name);
            if (value != null && !value.toString().isBlank()) {
                clauses.add(term(field, value));
            }
        });
        final var weeks = weekStarts(start, end);
        final var byOrigin = FiltersAggregator.builder().name(ORIGIN)
                .filters(originFilters())
                .subAggregators(subAggregators(weeks, end, "ingestedAt"))
                .build();
        final var search = storageManager.search(INDEX_REFERENCE, BoolQuery.builder().filterClauses(clauses).build(),
                new TypeReference<ReviewFeedbackDocument>() { }, List.of(), List.<Aggregator>of(byOrigin), 0, 0);
        final var origins = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(ORIGIN);
        final var buckets = origins == null || origins.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : origins.getBuckets();
        return new ReviewFeedbackMetricsResponse(start, end, origin(buckets.get(ARNOLD), weeks),
                origin(buckets.get(WORKFLOW), weeks), authorship(start, end, weeks));
    }

    private Authorship authorship(final long start, final long end, final List<Long> weeks) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                RangeQuery.builder().field("createdAt").from(start).to(end).includeUpper(false).build(),
                term("state.keyword", "published"),
                term("deleted", false))).build();
        final var ai = term("authorUsername.keyword", AI_AUTHOR);
        final var authorFilters = new LinkedHashMap<String, Query>();
        authorFilters.put(AI, ai);
        authorFilters.put(HUMAN, BoolQuery.builder().mustNot(ai).build());
        final var byAuthor = FiltersAggregator.builder().name(AUTHOR).filters(authorFilters)
                .subAggregators(List.of(
                        FiltersAggregator.builder().name(RESOLVABLE).filters(Map.of(RESOLVABLE, term(RESOLVABLE, true)))
                                .subAggregator(FiltersAggregator.builder().name(RESOLVED)
                                        .filters(Map.of(RESOLVED, term(RESOLVED, true))).build()).build(),
                        CardinalityAggregator.builder().name(MERGE_REQUESTS).field("mergeRequestId.keyword").build(),
                        FiltersAggregator.builder().name(BY_MR_AUTHOR)
                                .filters(Map.of(BY_MR_AUTHOR, term(BY_MR_AUTHOR, true))).build(),
                        weekAggregator(weeks, end, "createdAt", null)))
                .build();
        final var search = storageManager.search(SearchIndexReferences.COMMENTS, query,
                new TypeReference<CommentDocument>() { }, List.of(), List.<Aggregator>of(byAuthor), 0, 0);
        final var authors = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(AUTHOR);
        final var buckets = authors == null || authors.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : authors.getBuckets();
        return new Authorship(AI_AUTHOR, authors(buckets.get(AI), weeks), authors(buckets.get(HUMAN), weeks));
    }

    private static Authors authors(final FiltersAggregation.Bucket bucket, final List<Long> weeks) {
        final var aggregations = bucket == null || bucket.getAggregations() == null
                ? Map.<String, Aggregation>of() : bucket.getAggregations();
        final var resolvable = only(aggregations.get(RESOLVABLE), RESOLVABLE);
        final var resolved = resolvable == null || resolvable.getAggregations() == null ? null
                : only(resolvable.getAggregations().get(RESOLVED), RESOLVED);
        final var resolvableCount = resolvable == null ? 0 : resolvable.getCount();
        final var resolvedCount = resolved == null ? 0 : resolved.getCount();
        final var byMrAuthor = only(aggregations.get(BY_MR_AUTHOR), BY_MR_AUTHOR);
        final var mergeRequests = aggregations.get(MERGE_REQUESTS) instanceof CardinalityAggregation cardinality
                ? cardinality.getCount() : 0;
        final var weekBuckets = aggregations.get(WEEKS) instanceof FiltersAggregation byWeek && byWeek.getBuckets() != null
                ? byWeek.getBuckets() : Map.<String, FiltersAggregation.Bucket>of();
        final var weekly = new ArrayList<CommentWeek>();
        for (var index = 0; index < weeks.size(); index++) {
            final var week = weekBuckets.get(weekKey(index));
            weekly.add(new CommentWeek(weekStart(weeks.get(index)), week == null ? 0 : week.getCount()));
        }
        return new Authors(bucket == null ? 0 : bucket.getCount(), resolvableCount, resolvedCount,
                rate(resolvedCount, resolvableCount), mergeRequests, byMrAuthor == null ? 0 : byMrAuthor.getCount(),
                List.copyOf(weekly));
    }

    private static FiltersAggregation.Bucket only(final Aggregation aggregation, final String key) {
        return aggregation instanceof FiltersAggregation filters && filters.getBuckets() != null
                ? filters.getBuckets().get(key) : null;
    }

    private static Map<String, Query> originFilters() {
        final var arnold = BoolQuery.builder().shouldClauses(List.of(term("origin.keyword", ARNOLD),
                FieldExistsQuery.builder().field("analysisRunId").build())).minimumMatch("1").build();
        final var filters = new LinkedHashMap<String, Query>();
        filters.put(ARNOLD, arnold);
        filters.put(WORKFLOW, BoolQuery.builder().mustNot(arnold).build());
        return filters;
    }

    private static List<Aggregator> subAggregators(final List<Long> weeks, final long end, final String timeField) {
        final var aggregators = new ArrayList<Aggregator>();
        aggregators.add(terms(ACCEPTANCE, "acceptanceStatus.keyword", TOP_N));
        DIMENSIONS.forEach((name, field) -> aggregators.add(terms(name, field, TOP_N)));
        aggregators.add(StatsAggregator.builder().name(SCORE).field("score").build());
        aggregators.add(weekAggregator(weeks, end, timeField, terms(ACCEPTANCE, "acceptanceStatus.keyword", TOP_N)));
        return aggregators;
    }

    private static Aggregator weekAggregator(final List<Long> weeks, final long end, final String timeField,
                                             final Aggregator subAggregator) {
        final var weekFilters = new LinkedHashMap<String, Query>();
        for (var index = 0; index < weeks.size(); index++) {
            final var weekEnd = index + 1 < weeks.size() ? weeks.get(index + 1) : end;
            weekFilters.put(weekKey(index), RangeQuery.builder().field(timeField).from(weeks.get(index))
                    .to(weekEnd).includeUpper(false).build());
        }
        final var builder = FiltersAggregator.builder().name(WEEKS).filters(weekFilters);
        return (subAggregator == null ? builder : builder.subAggregator(subAggregator)).build();
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
            result.add(new Week(weekStart(weeks.get(index)), total, accepted,
                    rate(accepted, total - acceptance.getOrDefault("not_applicable", 0L))));
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

    private static String weekStart(final long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString();
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
