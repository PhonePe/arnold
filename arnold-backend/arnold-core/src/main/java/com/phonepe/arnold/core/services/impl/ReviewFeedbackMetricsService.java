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

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.columbus.search.aggregation.Aggregation;
import com.phonepe.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.columbus.search.aggregation.bucket.TermsAggregation;
import com.phonepe.columbus.search.aggregation.metrics.CardinalityAggregation;
import com.phonepe.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.columbus.search.aggregator.Aggregator;
import com.phonepe.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.columbus.search.aggregator.bucket.TermsAggregator;
import com.phonepe.columbus.search.aggregator.metrics.CardinalityAggregator;
import com.phonepe.columbus.search.aggregator.metrics.StatsAggregator;
import com.phonepe.columbus.search.query.Query;
import com.phonepe.columbus.search.query.compound.BoolQuery;
import com.phonepe.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.columbus.search.query.term.TermsQuery;
import com.phonepe.arnold.models.es.MergeRequestDocument;
import com.phonepe.columbus.search.query.term.RangeQuery;
import com.phonepe.columbus.search.query.term.TermQuery;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Authors;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Authorship;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.CommentWeek;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Origin;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.PersonaSplit;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Score;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Week;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Engagement;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.FocusArea;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.GroupRuns;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.FocusWeek;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.Runs;
import com.phonepe.arnold.models.admin.ReviewFeedbackMetricsResponse.SmartDiffs;
import com.phonepe.arnold.models.es.SmartDiffDocument;
import com.phonepe.arnold.models.es.AnalysisRunDocument;
import com.phonepe.arnold.models.es.CommentDocument;
import com.phonepe.arnold.models.es.GroupAnalysisRunDocument;
import com.phonepe.arnold.models.es.ReviewEngagementDocument;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;

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

import static com.phonepe.arnold.core.common.Constants.FIELD_NOTE_CREATED_AT;
import static com.phonepe.arnold.core.common.Constants.INDEX_REFERENCE;

/**
 * Aggregates review-feedback outcomes from the {@code CORE_REVIEWS} index in a single search, bucketed by origin.
 * Feedback is attributed to Arnold when {@code origin=arnold} or it carries an {@code analysisRunId}; the latter
 * covers documents ingested before {@code origin} existed. The previous-workflow bucket holds the remaining feedback
 * authored by the configured AI comment author. A second search over {@code CORE_COMMENTS} splits all
 * published MR comments into AI (authored by the configured AI comment author) and human. Both searches select by when the note was
 * posted and keep only merge requests merged on or after the range start (comments on merged MRs only).
 */
@Singleton
public class ReviewFeedbackMetricsService {
    static final long DAY_MS = 24L * 60 * 60 * 1000;
    static final long WEEK_MS = 7 * DAY_MS;
    static final long MAX_RANGE_MS = 366 * DAY_MS;
    // ponytail: weekly buckets capped to the most recent 27; switch to monthly buckets if longer trends matter.
    static final int MAX_WEEKS = 27;
    private static final int TOP_N = 10;
    private static final int MERGED_PAGE = 1000;
    private static final int FOCUS_AREAS = 100;
    private static final int MAX_MERGED_REQUESTS = 10000;
    private static final String ORIGIN = "origin";
    private static final String ARNOLD = ReviewFeedbackDocument.ORIGIN_ARNOLD;
    private static final String WORKFLOW = ReviewFeedbackDocument.ORIGIN_WORKFLOW;
    private static final String PERSONA = "persona";
    private static final String GENERAL = "general";
    private static final String ACCEPTANCE = "acceptance";
    private static final String SCORE = "score";
    private static final String WEEKS = "weeks";
    private static final String PROJECT = "project";
    private static final String MODEL = "model";
    private static final String ACCEPTANCE_STATUS_FIELD = "acceptanceStatus.keyword";
    private static final String PRIORITY = "priority";
    private static final String COMMENT_TYPE = "commentType";
    private static final String CATEGORY = "category";
    private static final String HAS_SUGGESTION = "hasSuggestion";
    private static final String NOT_APPLICABLE = "not_applicable";
    private static final String STATUS = "status";
    private static final String TYPE_FIELD = "type.keyword";
    private static final String CREATED_AT = "createdAt";
    private static final String STATUS_FIELD = "status.keyword";
    private static final String DURATION = "duration";
    private static final String DURATION_MS = "durationMs";
    private static final String FINDINGS = "findings";
    private static final String POSTED = "posted";
    private static final String TOTAL_TOKENS = "totalTokens";
    private static final String TOKENS = "tokens";
    private static final String REQUESTED_AT = "requestedAt";
    private static final String NOT_USEFUL = "notUseful";
    private static final String MERGE_REQUEST_ID_FIELD = "mergeRequestId.keyword";
    private static final String USEFUL = "useful";
    private static final Map<String, String> DIMENSIONS = dimensions();
    static final Map<String, String> FILTER_FIELDS = Map.of(
            PROJECT, "project.projectName.keyword",
            MODEL, "sourceModel.keyword",
            "personaId", "personaId.keyword",
            STATUS, ACCEPTANCE_STATUS_FIELD,
            PRIORITY, "priority.keyword",
            COMMENT_TYPE, "commentType.keyword",
            CATEGORY, "commentCategory.keyword",
            HAS_SUGGESTION, HAS_SUGGESTION);
    private static final String AI = "ai";
    private static final String HUMAN = "human";
    private static final String AUTHOR = "author";
    private static final String RESOLVABLE = "resolvable";
    private static final String RESOLVED = "resolved";
    private static final String MERGE_REQUESTS = "mergeRequests";
    private static final String BY_MR_AUTHOR = "byMergeRequestAuthor";

    private final ESStorageManager storageManager;
    private final String aiAuthor;

    @Inject
    public ReviewFeedbackMetricsService(final ESStorageManager storageManager,
                                        final ArnoldCoreConfiguration configuration) {
        this.storageManager = storageManager;
        this.aiAuthor = configuration.getAnalysisConfiguration().getAiCommentAuthor();
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
        clauses.add(RangeQuery.builder().field(FIELD_NOTE_CREATED_AT).from(start).to(end).includeUpper(false).build());
        // Only inline (diff) notes are resolvable threads.
        clauses.add(FieldExistsQuery.builder().field("position").build());
        FILTER_FIELDS.forEach((name, field) -> {
            final var value = filters == null ? null : filters.get(name);
            if (value != null && !value.toString().isBlank()) {
                clauses.add(term(field, value));
            }
        });
        final var merged = mergedRequests(start);
        clauses.add(TermsQuery.builder().field("mrId").values(merged.providerIds()).build());
        final var weeks = weekStarts(start, end);
        final var byOrigin = FiltersAggregator.builder().name(ORIGIN)
                .filters(originFilters(aiAuthor))
                .subAggregators(subAggregators(weeks, end, FIELD_NOTE_CREATED_AT))
                .build();
        final var search = storageManager.search(INDEX_REFERENCE, BoolQuery.builder().filterClauses(clauses).build(),
                new TypeReference<ReviewFeedbackDocument>() { }, List.of(), List.<Aggregator>of(byOrigin), 0, 0);
        final var origins = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(ORIGIN);
        final var buckets = origins == null || origins.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : origins.getBuckets();
        final var arnold = origin(buckets.get(ARNOLD), weeks);
        final var workflow = origin(buckets.get(WORKFLOW), weeks);
        return new ReviewFeedbackMetricsResponse(start, end, arnold, workflow,
                authorship(start, end, weeks, merged.ids(), origin(buckets.get(AI), weeks), origin(buckets.get(HUMAN), weeks)),
                new PersonaSplit(origin(buckets.get(PERSONA), weeks), origin(buckets.get(GENERAL), weeks)),
                runs(start, end, weeks), smartDiffs(start, end, weeks), engagement(start, end, weeks),
                groupRuns(start, end, weeks));
    }

    private Runs runs(final long start, final long end, final List<Long> weeks) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                term(TYPE_FIELD, AnalysisRunDocument.DOCUMENT_TYPE),
                RangeQuery.builder().field(CREATED_AT).from(start).to(end).includeUpper(false).build())).build();
        final var aggregations = aggregations(SearchIndexReferences.ANALYSIS_RUNS, query,
                new TypeReference<AnalysisRunDocument>() { }, List.of(
                        terms("outcome", "outcome.keyword", TOP_N), terms(STATUS, STATUS_FIELD, TOP_N),
                        StatsAggregator.builder().name(DURATION).field(DURATION_MS).build(),
                        StatsAggregator.builder().name(FINDINGS).field("findingCount").build(),
                        StatsAggregator.builder().name(POSTED).field("postedFindings").build(),
                        StatsAggregator.builder().name(TOKENS).field(TOTAL_TOKENS).build(),
                        weekAggregator(weeks, end, CREATED_AT, null)));
        final var duration = stats(aggregations.get(DURATION));
        return new Runs(duration == null ? 0 : duration.getCount(), counts(aggregations.get("outcome")),
                counts(aggregations.get(STATUS)),
                duration == null || duration.getCount() == 0 ? null : duration.getAverage(),
                sum(aggregations.get(FINDINGS)), sum(aggregations.get(POSTED)), sum(aggregations.get(TOKENS)),
                commentWeeks(aggregations.get(WEEKS), weeks));
    }

    private GroupRuns groupRuns(final long start, final long end, final List<Long> weeks) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                term(TYPE_FIELD, GroupAnalysisRunDocument.DOCUMENT_TYPE),
                RangeQuery.builder().field(CREATED_AT).from(start).to(end).includeUpper(false).build())).build();
        final var aggregations = aggregations(SearchIndexReferences.ANALYSIS_RUNS, query,
                new TypeReference<GroupAnalysisRunDocument>() { }, List.of(
                        terms(STATUS, STATUS_FIELD, TOP_N),
                        CardinalityAggregator.builder().name("groups").field("groupId.keyword").build(),
                        CardinalityAggregator.builder().name("invokers").field("invokedBy.keyword").build(),
                        StatsAggregator.builder().name(DURATION).field(DURATION_MS).build(),
                        StatsAggregator.builder().name(FINDINGS).field("findingCount").build(),
                        StatsAggregator.builder().name(POSTED).field("postedFindings").build(),
                        StatsAggregator.builder().name(TOKENS).field(TOTAL_TOKENS).build(),
                        weekAggregator(weeks, end, CREATED_AT, null)));
        final var duration = stats(aggregations.get(DURATION));
        return new GroupRuns(duration == null ? 0 : duration.getCount(), cardinality(aggregations.get("groups")),
                cardinality(aggregations.get("invokers")), counts(aggregations.get(STATUS)),
                duration == null || duration.getCount() == 0 ? null : duration.getAverage(),
                sum(aggregations.get(FINDINGS)), sum(aggregations.get(POSTED)), sum(aggregations.get(TOKENS)),
                commentWeeks(aggregations.get(WEEKS), weeks));
    }

    private SmartDiffs smartDiffs(final long start, final long end, final List<Long> weeks) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                term(TYPE_FIELD, SmartDiffDocument.DOCUMENT_TYPE),
                RangeQuery.builder().field(REQUESTED_AT).from(start).to(end).includeUpper(false).build())).build();
        final var aggregations = aggregations(SearchIndexReferences.ANALYSIS_RUNS, query,
                new TypeReference<SmartDiffDocument>() { }, List.of(
                        terms(STATUS, STATUS_FIELD, TOP_N),
                        terms("assessment", "commentAssessmentStatus.keyword", TOP_N),
                        StatsAggregator.builder().name(DURATION).field(DURATION_MS).build(),
                        StatsAggregator.builder().name(TOKENS).field(TOTAL_TOKENS).build(),
                        weekAggregator(weeks, end, REQUESTED_AT, null)));
        final var duration = stats(aggregations.get(DURATION));
        return new SmartDiffs(duration == null ? 0 : duration.getCount(), counts(aggregations.get(STATUS)),
                counts(aggregations.get("assessment")),
                duration == null || duration.getCount() == 0 ? null : duration.getAverage(),
                sum(aggregations.get(TOKENS)), commentWeeks(aggregations.get(WEEKS), weeks));
    }

    private Engagement engagement(final long start, final long end, final List<Long> weeks) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                RangeQuery.builder().field("updatedAt").from(start).to(end).includeUpper(false).build())).build();
        final var aggregations = aggregations(SearchIndexReferences.REVIEW_ENGAGEMENT, query,
                new TypeReference<ReviewEngagementDocument>() { }, List.of(
                        CardinalityAggregator.builder().name("reviewers").field("userId.keyword").build(),
                        CardinalityAggregator.builder().name(MERGE_REQUESTS).field(MERGE_REQUEST_ID_FIELD).build(),
                        StatsAggregator.builder().name("rated").field("focusAreasRated").build(),
                        StatsAggregator.builder().name(USEFUL).field("focusAreasUseful").build(),
                        StatsAggregator.builder().name(NOT_USEFUL).field("focusAreasNotUseful").build(),
                        terms("usefulAreas", "usefulAreas.keyword", FOCUS_AREAS),
                        terms("notUsefulAreas", "notUsefulAreas.keyword", FOCUS_AREAS),
                        weekAggregators(weeks, end, "updatedAt", List.<Aggregator>of(
                                StatsAggregator.builder().name(USEFUL).field("focusAreasUseful").build(),
                                StatsAggregator.builder().name(NOT_USEFUL).field("focusAreasNotUseful").build()))));
        final var generated = generatedFocus(start, end);
        final var rated = sum(aggregations.get("rated"));
        final var useful = sum(aggregations.get(USEFUL));
        return new Engagement(cardinality(aggregations.get("reviewers")), cardinality(aggregations.get(MERGE_REQUESTS)),
                sum(generated.get("count")), rated, useful, sum(aggregations.get(NOT_USEFUL)), rate(useful, rated),
                focusAreas(counts(generated.get("areas")), counts(aggregations.get("usefulAreas")),
                        counts(aggregations.get("notUsefulAreas"))),
                focusWeeks(aggregations.get(WEEKS), weeks));
    }

    private Map<String, Aggregation> generatedFocus(final long start, final long end) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                term(TYPE_FIELD, SmartDiffDocument.DOCUMENT_TYPE), term(STATUS_FIELD, "completed"),
                RangeQuery.builder().field(REQUESTED_AT).from(start).to(end).includeUpper(false).build())).build();
        return aggregations(SearchIndexReferences.ANALYSIS_RUNS, query, new TypeReference<SmartDiffDocument>() { },
                List.of(StatsAggregator.builder().name("count").field("focusAreaCount").build(),
                        terms("areas", "focusAreas.keyword", FOCUS_AREAS)));
    }

    private static List<FocusArea> focusAreas(final Map<String, Long> generated, final Map<String, Long> useful,
                                              final Map<String, Long> notUseful) {
        final var names = new java.util.LinkedHashSet<>(useful.keySet());
        names.addAll(notUseful.keySet());
        names.addAll(generated.keySet());
        return names.stream().map(name -> {
            final var up = useful.getOrDefault(name, 0L);
            final var down = notUseful.getOrDefault(name, 0L);
            return new FocusArea(name, up, down, rate(up, up + down));
        }).sorted(java.util.Comparator.comparingLong((FocusArea area) -> area.useful() + area.notUseful()).reversed())
                .limit(FOCUS_AREAS).toList();
    }

    private static List<FocusWeek> focusWeeks(final Aggregation aggregation, final List<Long> weeks) {
        final var buckets = aggregation instanceof FiltersAggregation byWeek && byWeek.getBuckets() != null
                ? byWeek.getBuckets() : Map.<String, FiltersAggregation.Bucket>of();
        final var result = new ArrayList<FocusWeek>();
        for (var index = 0; index < weeks.size(); index++) {
            final var week = buckets.get(weekKey(index));
            final var inner = week == null || week.getAggregations() == null
                    ? Map.<String, Aggregation>of() : week.getAggregations();
            result.add(new FocusWeek(weekStart(weeks.get(index)), sum(inner.get(USEFUL)),
                    sum(inner.get(NOT_USEFUL))));
        }
        return List.copyOf(result);
    }

    private <T> Map<String, Aggregation> aggregations(final String index, final Query query,
                                                      final TypeReference<T> type, final List<Aggregator> aggregators) {
        final var search = storageManager.search(index, query, type, List.of(), aggregators, 0, 0);
        return search == null || search.getAggregations() == null ? Map.of() : search.getAggregations();
    }

    private static StatsAggregation stats(final Aggregation aggregation) {
        return aggregation instanceof StatsAggregation stats ? stats : null;
    }

    private static long sum(final Aggregation aggregation) {
        final var stats = stats(aggregation);
        return stats == null ? 0 : Math.round(stats.getSum());
    }

    private static long cardinality(final Aggregation aggregation) {
        return aggregation instanceof CardinalityAggregation cardinality ? cardinality.getCount() : 0;
    }

    private static List<CommentWeek> commentWeeks(final Aggregation aggregation, final List<Long> weeks) {
        final var buckets = aggregation instanceof FiltersAggregation byWeek && byWeek.getBuckets() != null
                ? byWeek.getBuckets() : Map.<String, FiltersAggregation.Bucket>of();
        final var result = new ArrayList<CommentWeek>();
        for (var index = 0; index < weeks.size(); index++) {
            final var week = buckets.get(weekKey(index));
            result.add(new CommentWeek(weekStart(weeks.get(index)), week == null ? 0 : week.getCount()));
        }
        return List.copyOf(result);
    }

    private record MergedRequests(List<String> ids, List<Long> providerIds) {
    }

    // Comments in the range on an MR merged before the range start were posted after the merge, so they are not review
    // activity; this lower bound excludes them and keeps the id list small.
    private MergedRequests mergedRequests(final long start) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                RangeQuery.builder().field("mergedAt").from(start).includeLower(true).build())).build();
        final var ids = new ArrayList<String>();
        final var providerIds = new ArrayList<Long>();
        for (var offset = 0; offset < MAX_MERGED_REQUESTS; offset += MERGED_PAGE) {
            final var page = storageManager.searchEntities(SearchIndexReferences.CHANGE_REQUESTS, query,
                    new TypeReference<MergeRequestDocument>() { }, List.of(), MERGED_PAGE, offset);
            page.forEach(mr -> {
                ids.add(mr.id());
                try {
                    providerIds.add(Long.parseLong(mr.providerId()));
                } catch (NumberFormatException ignored) {
                    // not a numeric provider id; cannot match feedback documents
                }
            });
            if (page.size() < MERGED_PAGE) {
                break;
            }
        }
        return new MergedRequests(List.copyOf(ids), List.copyOf(providerIds));
    }

    private Authorship authorship(final long start, final long end, final List<Long> weeks,
                                final List<String> mergeRequestIds, final Origin aiFeedback, final Origin humanFeedback) {
        final var query = BoolQuery.builder().filterClauses(List.of(
                RangeQuery.builder().field(CREATED_AT).from(start).to(end).includeUpper(false).build(),
                term("state.keyword", "published"),
                term(RESOLVABLE, true),
                TermsQuery.builder().field(MERGE_REQUEST_ID_FIELD).values(mergeRequestIds).build(),
                term("deleted", false))).build();
        final var ai = term("authorUsername.keyword", aiAuthor);
        final var authorFilters = new LinkedHashMap<String, Query>();
        authorFilters.put(AI, ai);
        authorFilters.put(HUMAN, BoolQuery.builder().mustNot(ai).build());
        final var byAuthor = FiltersAggregator.builder().name(AUTHOR).filters(authorFilters)
                .subAggregators(List.of(
                        FiltersAggregator.builder().name(RESOLVABLE).filters(Map.of(RESOLVABLE, term(RESOLVABLE, true)))
                                .subAggregator(FiltersAggregator.builder().name(RESOLVED)
                                        .filters(Map.of(RESOLVED, term(RESOLVED, true))).build()).build(),
                        CardinalityAggregator.builder().name(MERGE_REQUESTS).field(MERGE_REQUEST_ID_FIELD).build(),
                        FiltersAggregator.builder().name(BY_MR_AUTHOR)
                                .filters(Map.of(BY_MR_AUTHOR, term(BY_MR_AUTHOR, true))).build(),
                        weekAggregator(weeks, end, CREATED_AT, null)))
                .build();
        final var search = storageManager.search(SearchIndexReferences.COMMENTS, query,
                new TypeReference<CommentDocument>() { }, List.of(), List.<Aggregator>of(byAuthor), 0, 0);
        final var authors = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(AUTHOR);
        final var buckets = authors == null || authors.getBuckets() == null
                ? Map.<String, FiltersAggregation.Bucket>of() : authors.getBuckets();
        return new Authorship(aiAuthor, authors(buckets.get(AI), weeks, aiFeedback),
                authors(buckets.get(HUMAN), weeks, humanFeedback));
    }

    private static Authors authors(final FiltersAggregation.Bucket bucket, final List<Long> weeks,
                                   final Origin feedback) {
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
                List.copyOf(weekly), feedback);
    }

    private static FiltersAggregation.Bucket only(final Aggregation aggregation, final String key) {
        return aggregation instanceof FiltersAggregation filters && filters.getBuckets() != null
                ? filters.getBuckets().get(key) : null;
    }

    private static Map<String, Query> originFilters(final String aiAuthor) {
        final var arnold = BoolQuery.builder().shouldClauses(List.of(term("origin.keyword", ARNOLD),
                FieldExistsQuery.builder().field("analysisRunId").build())).minimumMatch("1").build();
        final var filters = new LinkedHashMap<String, Query>();
        filters.put(ARNOLD, arnold);
        final var aiReviewer = term("reviewer.username.keyword", aiAuthor);
        // Previous workflow is limited to AI feedback so it compares like-for-like with Arnold.
        filters.put(WORKFLOW, BoolQuery.builder().filterClauses(List.of(aiReviewer)).mustNot(arnold).build());
        filters.put(AI, aiReviewer);
        filters.put(HUMAN, BoolQuery.builder().mustNot(aiReviewer).build());
        final var hasPersona = FieldExistsQuery.builder().field("personaId").build();
        // Overlaps the arnold bucket: persona + general = arnold.
        filters.put(PERSONA, BoolQuery.builder().filterClauses(List.of(arnold, hasPersona)).build());
        filters.put(GENERAL, BoolQuery.builder().filterClauses(List.of(arnold)).mustNot(hasPersona).build());
        return filters;
    }

    private static List<Aggregator> subAggregators(final List<Long> weeks, final long end, final String timeField) {
        final var aggregators = new ArrayList<Aggregator>();
        aggregators.add(terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD, TOP_N));
        DIMENSIONS.forEach((name, field) -> aggregators.add(terms(name, field, TOP_N)));
        aggregators.add(StatsAggregator.builder().name(SCORE).field(SCORE).build());
        aggregators.add(weekAggregator(weeks, end, timeField, terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD, TOP_N)));
        return aggregators;
    }

    private static Aggregator weekAggregator(final List<Long> weeks, final long end, final String timeField,
                                             final Aggregator subAggregator) {
        return weekAggregators(weeks, end, timeField,
                subAggregator == null ? List.<Aggregator>of() : List.of(subAggregator));
    }

    private static Aggregator weekAggregators(final List<Long> weeks, final long end, final String timeField,
                                              final List<Aggregator> subAggregators) {
        final var weekFilters = new LinkedHashMap<String, Query>();
        for (var index = 0; index < weeks.size(); index++) {
            final var weekEnd = index + 1 < weeks.size() ? weeks.get(index + 1) : end;
            weekFilters.put(weekKey(index), RangeQuery.builder().field(timeField).from(weeks.get(index))
                    .to(weekEnd).includeUpper(false).build());
        }
        return FiltersAggregator.builder().name(WEEKS).filters(weekFilters).subAggregators(subAggregators).build();
    }

    private static Origin origin(final FiltersAggregation.Bucket bucket, final List<Long> weeks) {
        final var aggregations = bucket == null || bucket.getAggregations() == null
                ? Map.<String, Aggregation>of() : bucket.getAggregations();
        final var total = bucket == null ? 0 : bucket.getCount();
        final var acceptance = counts(aggregations.get(ACCEPTANCE));
        final var breakdowns = new LinkedHashMap<String, Map<String, Long>>();
        DIMENSIONS.keySet().forEach(name -> breakdowns.put(name, counts(aggregations.get(name))));
        final var accepted = accepted(acceptance);
        final var actionable = total - acceptance.getOrDefault(NOT_APPLICABLE, 0L);
        return new Origin(total, accepted, actionable, rate(accepted, actionable),
                score((StatsAggregation) aggregations.get(SCORE)), acceptance, breakdowns.get(PRIORITY),
                breakdowns.get(COMMENT_TYPE), suggestion(breakdowns.get("suggestion")), breakdowns.get(CATEGORY),
                breakdowns.get(MODEL), breakdowns.get(PERSONA), breakdowns.get(PROJECT),
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
                    rate(accepted, total - acceptance.getOrDefault(NOT_APPLICABLE, 0L))));
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
        dimensions.put(PRIORITY, "priority.keyword");
        dimensions.put(COMMENT_TYPE, "commentType.keyword");
        dimensions.put("suggestion", HAS_SUGGESTION);
        dimensions.put(CATEGORY, "commentCategory.keyword");
        dimensions.put(MODEL, "sourceModel.keyword");
        dimensions.put(PERSONA, "personaId.keyword");
        dimensions.put(PROJECT, "project.projectName.keyword");
        return Map.copyOf(dimensions);
    }
}
