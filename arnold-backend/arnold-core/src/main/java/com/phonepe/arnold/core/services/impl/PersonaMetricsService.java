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
import com.phonepe.columbus.search.aggregator.Aggregator;
import com.phonepe.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.columbus.search.aggregator.bucket.TermsAggregator;
import com.phonepe.columbus.search.query.Query;
import com.phonepe.columbus.search.query.compound.BoolQuery;
import com.phonepe.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.columbus.search.query.term.RangeQuery;
import com.phonepe.columbus.search.query.term.TermQuery;
import com.phonepe.columbus.search.sort.Sort;
import com.phonepe.columbus.storage.models.result.SearchResult;
import com.phonepe.arnold.core.elastic.ESStorageManager;
import com.phonepe.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.models.es.AnalysisFindingDocument;
import com.phonepe.arnold.models.es.AnalysisRunDocument;
import com.phonepe.arnold.models.es.CommentDocument;
import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.persona.PersonaMetricsResponse;
import com.phonepe.arnold.models.persona.PersonaMetricsSummaryResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongConsumer;

import static com.phonepe.arnold.core.common.Constants.INDEX_REFERENCE;
import static com.phonepe.arnold.core.services.impl.ReviewFeedbackMetricsService.DAY_MS;
import static com.phonepe.arnold.core.services.impl.ReviewFeedbackMetricsService.MAX_RANGE_MS;
import static com.phonepe.arnold.core.services.impl.ReviewFeedbackMetricsService.weekStarts;

/**
 * Per-persona effectiveness from three projections: run and finding documents in {@code CORE_ANALYSIS_RUNS},
 * review feedback in {@code CORE_REVIEWS} and MR comments in {@code CORE_COMMENTS}. Each search returns aggregations
 * only. Findings and comments created before persona attribution existed carry no persona and count as general.
 */
@Singleton
public class PersonaMetricsService {
    private static final int MAX_TERMS = 100;
    private static final String RUNS = "runs";
    private static final String FINDINGS = "findings";
    private static final String FEEDBACK = "feedback";
    private static final String GENERAL = "general";
    private static final String PERSONAS = "personas";
    private static final String VERSIONS = "versions";
    private static final String STATUS = "status";
    private static final String POSTED = "posted";
    private static final String TYPE_FIELD = "type.keyword";
    private static final String PERSONA_ID_FIELD = "personaId.keyword";
    private static final String CREATED_AT = "createdAt";
    private static final String INGESTED_AT = "ingestedAt";
    private static final String ACCEPTANCE_STATUS_FIELD = "acceptanceStatus.keyword";
    private static final String NOT_APPLICABLE = "not_applicable";
    private static final String ACCEPTANCE = "acceptance";
    private static final String WEEKS = "weeks";
    private static final String COMMENTS = "comments";
    private static final String RESOLVABLE = "resolvable";
    private static final String RESOLVED = "resolved";

    private final ESStorageManager storageManager;

    @Inject
    public PersonaMetricsService(final ESStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public PersonaMetricsResponse metrics(final String personaId, final Long from, final Long to) {
        final var range = range(from, to);
        final var weeks = weekStarts(range[0], range[1]);

        final var runFilters = new LinkedHashMap<String, Query>();
        runFilters.put(RUNS, BoolQuery.builder().filterClauses(List.of(term(TYPE_FIELD, AnalysisRunDocument.DOCUMENT_TYPE),
                term("reviewPersonaIds.keyword", personaId))).build());
        runFilters.put(FINDINGS, BoolQuery.builder().filterClauses(List.of(
                term(TYPE_FIELD, AnalysisFindingDocument.DOCUMENT_TYPE), term(PERSONA_ID_FIELD, personaId))).build());
        final var runs = buckets(search(SearchIndexReferences.ANALYSIS_RUNS,
                range(CREATED_AT, range), new TypeReference<AnalysisRunDocument>() { }, List.of(),
                List.<Aggregator>of(FiltersAggregator.builder().name(RUNS).filters(runFilters).subAggregators(List.of(
                        terms(VERSIONS, "personaVersions.keyword"), terms("findingVersions", "personaVersion"),
                        terms(STATUS, "publicationStatus.keyword"), weeks(weeks, range[1], CREATED_AT, null)))
                        .build()), 0, 0), RUNS);

        final var feedback = buckets(search(INDEX_REFERENCE, range(INGESTED_AT, range),
                new TypeReference<ReviewFeedbackDocument>() { }, List.of(), List.<Aggregator>of(
                        FiltersAggregator.builder().name(FEEDBACK).filters(Map.of(FEEDBACK,
                                term(PERSONA_ID_FIELD, personaId))).subAggregators(List.of(
                                terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD),
                                TermsAggregator.builder().name(VERSIONS).field("personaVersion").size(MAX_TERMS)
                                        .subAggregator(terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD)).build(),
                                weeks(weeks, range[1], INGESTED_AT, terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD))))
                                .build()), 0, 0), FEEDBACK).get(FEEDBACK);

        final var comments = buckets(search(SearchIndexReferences.COMMENTS, BoolQuery.builder()
                        .filterClauses(List.of(rangeQuery(CREATED_AT, range), term("state.keyword", "published"),
                                term("deleted", false))).build(),
                new TypeReference<CommentDocument>() { }, List.of(), List.<Aggregator>of(
                        FiltersAggregator.builder().name(COMMENTS).filters(Map.of(COMMENTS,
                                        term(PERSONA_ID_FIELD, personaId)))
                                .subAggregator(FiltersAggregator.builder().name(RESOLVABLE)
                                        .filters(Map.of(RESOLVABLE, term(RESOLVABLE, true)))
                                        .subAggregator(FiltersAggregator.builder().name(RESOLVED)
                                                .filters(Map.of(RESOLVED, term(RESOLVED, true))).build()).build())
                                .build()), 0, 0), COMMENTS).get(COMMENTS);

        final var findingsBucket = runs.get(FINDINGS);
        final var status = counts(sub(findingsBucket, STATUS));
        final var findings = new PersonaMetricsResponse.Findings(count(findingsBucket),
                status.getOrDefault(POSTED, 0L), status.getOrDefault("failed", 0L));
        final var acceptanceCounts = counts(sub(feedback, ACCEPTANCE));
        final var accepted = accepted(acceptanceCounts);
        final var actionable = count(feedback) - acceptanceCounts.getOrDefault(NOT_APPLICABLE, 0L);
        final var resolvable = only(sub(comments, RESOLVABLE), RESOLVABLE);
        final var resolved = only(sub(resolvable, RESOLVED), RESOLVED);
        return new PersonaMetricsResponse(personaId, range[0], range[1], count(runs.get(RUNS)), findings,
                new PersonaMetricsResponse.Acceptance(count(feedback), accepted, actionable, rate(accepted, actionable)),
                new PersonaMetricsResponse.Resolution(count(comments), count(resolvable), count(resolved),
                        rate(count(resolved), count(resolvable))),
                weekly(weeks, sub(findingsBucket, WEEKS), sub(feedback, WEEKS)),
                versions(personaId, runs.get(RUNS), findingsBucket, feedback));
    }

    public PersonaMetricsSummaryResponse summary(final Long from, final Long to) {
        final var range = range(from, to);
        final var posted = FiltersAggregator.builder().name(POSTED)
                .filters(Map.of(POSTED, term("publicationStatus.keyword", POSTED))).build();
        final var hasPersona = FieldExistsQuery.builder().field("personaId").build();
        final var runFilters = new LinkedHashMap<String, Query>();
        runFilters.put(RUNS, term(TYPE_FIELD, AnalysisRunDocument.DOCUMENT_TYPE));
        runFilters.put(FINDINGS, term(TYPE_FIELD, AnalysisFindingDocument.DOCUMENT_TYPE));
        final var runs = buckets(search(SearchIndexReferences.ANALYSIS_RUNS,
                range(CREATED_AT, range), new TypeReference<AnalysisRunDocument>() { }, List.of(),
                List.<Aggregator>of(FiltersAggregator.builder().name(RUNS).filters(runFilters).subAggregators(List.of(
                        terms(PERSONAS, "reviewPersonaIds.keyword"),
                        TermsAggregator.builder().name("findingPersonas").field(PERSONA_ID_FIELD).size(MAX_TERMS)
                                .subAggregator(posted).build(),
                        FiltersAggregator.builder().name(GENERAL).filters(Map.of(GENERAL,
                                BoolQuery.builder().mustNot(hasPersona).build())).subAggregator(posted).build()))
                        .build()), 0, 0), RUNS);

        final var arnold = BoolQuery.builder().shouldClauses(List.of(
                term("origin.keyword", ReviewFeedbackDocument.ORIGIN_ARNOLD),
                FieldExistsQuery.builder().field("analysisRunId").build())).minimumMatch("1").build();
        final var feedbackFilters = new LinkedHashMap<String, Query>();
        feedbackFilters.put(PERSONAS, hasPersona);
        feedbackFilters.put(GENERAL, BoolQuery.builder().filterClauses(List.of(arnold)).mustNot(hasPersona).build());
        final var feedback = buckets(search(INDEX_REFERENCE, range(INGESTED_AT, range),
                new TypeReference<ReviewFeedbackDocument>() { }, List.of(), List.<Aggregator>of(
                        FiltersAggregator.builder().name(FEEDBACK).filters(feedbackFilters).subAggregators(List.of(
                                TermsAggregator.builder().name(PERSONAS).field(PERSONA_ID_FIELD).size(MAX_TERMS)
                                        .subAggregator(terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD)).build(),
                                terms(ACCEPTANCE, ACCEPTANCE_STATUS_FIELD))).build()), 0, 0), FEEDBACK);

        final var runCounts = counts(sub(runs.get(RUNS), PERSONAS));
        final var findingBuckets = termBuckets(sub(runs.get(FINDINGS), "findingPersonas"));
        final var feedbackBuckets = termBuckets(sub(feedback.get(PERSONAS), PERSONAS));
        final var ids = new TreeSet<String>();
        ids.addAll(runCounts.keySet());
        ids.addAll(findingBuckets.keySet());
        ids.addAll(feedbackBuckets.keySet());
        final var personas = ids.stream().map(id -> entry(id, runCounts.getOrDefault(id, 0L),
                findingBuckets.get(id), feedbackBuckets.get(id))).toList();
        final var generalFindings = only(sub(runs.get(FINDINGS), GENERAL), GENERAL);
        final var general = entry(null, count(runs.get(RUNS)), generalFindings, feedback.get(GENERAL));
        return new PersonaMetricsSummaryResponse(range[0], range[1], personas, general);
    }

    private static PersonaMetricsSummaryResponse.Entry entry(final String personaId, final long runs,
                                                             final FiltersAggregation.Bucket findings,
                                                             final FiltersAggregation.Bucket feedback) {
        final var acceptance = counts(sub(feedback, ACCEPTANCE));
        final var accepted = accepted(acceptance);
        final var total = count(feedback);
        return new PersonaMetricsSummaryResponse.Entry(personaId, runs, count(findings),
                count(only(sub(findings, POSTED), POSTED)), total, accepted,
                rate(accepted, total - acceptance.getOrDefault(NOT_APPLICABLE, 0L)));
    }

    private static List<PersonaMetricsResponse.Week> weekly(final List<Long> weeks, final Aggregation findings,
                                                            final Aggregation feedback) {
        final var result = new ArrayList<PersonaMetricsResponse.Week>();
        for (var index = 0; index < weeks.size(); index++) {
            final var findingWeek = only(findings, weekKey(index));
            final var feedbackWeek = only(feedback, weekKey(index));
            final var acceptance = counts(sub(feedbackWeek, ACCEPTANCE));
            final var accepted = accepted(acceptance);
            result.add(new PersonaMetricsResponse.Week(
                    Instant.ofEpochMilli(weeks.get(index)).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                    count(findingWeek), count(feedbackWeek), accepted,
                    rate(accepted, count(feedbackWeek) - acceptance.getOrDefault(NOT_APPLICABLE, 0L))));
        }
        return List.copyOf(result);
    }

    private static List<PersonaMetricsResponse.Version> versions(final String personaId,
                                                                 final FiltersAggregation.Bucket runs,
                                                                 final FiltersAggregation.Bucket findings,
                                                                 final FiltersAggregation.Bucket feedback) {
        final var prefix = personaId + "@";
        final var runCounts = new TreeMap<Long, Long>();
        counts(sub(runs, VERSIONS)).forEach((key, count) -> {
            if (key.startsWith(prefix)) {
                parse(key.substring(prefix.length()), version -> runCounts.put(version, count));
            }
        });
        final var findingCounts = new TreeMap<Long, Long>();
        counts(sub(findings, "findingVersions")).forEach((key, count) -> parse(key, v -> findingCounts.put(v, count)));
        final var feedbackBuckets = new TreeMap<Long, FiltersAggregation.Bucket>();
        termBuckets(sub(feedback, VERSIONS)).forEach((key, bucket) -> parse(key, v -> feedbackBuckets.put(v, bucket)));
        final var all = new TreeSet<Long>();
        all.addAll(runCounts.keySet());
        all.addAll(findingCounts.keySet());
        all.addAll(feedbackBuckets.keySet());
        return all.descendingSet().stream().map(version -> {
            final var bucket = feedbackBuckets.get(version);
            final var acceptance = counts(sub(bucket, ACCEPTANCE));
            final var accepted = accepted(acceptance);
            return new PersonaMetricsResponse.Version(version, runCounts.getOrDefault(version, 0L),
                    findingCounts.getOrDefault(version, 0L), count(bucket), accepted,
                    rate(accepted, count(bucket) - acceptance.getOrDefault(NOT_APPLICABLE, 0L)));
        }).toList();
    }

    private static void parse(final String raw, final LongConsumer consumer) {
        try {
            consumer.accept((long) Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            // Malformed version keys are skipped.
        }
    }

    private static long[] range(final Long from, final Long to) {
        final var end = to == null ? System.currentTimeMillis() : to;
        final var start = from == null ? end - 30 * DAY_MS : from;
        if (start >= end || end - start > MAX_RANGE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("from must be before to and the range must not exceed 366 days").build();
        }
        return new long[]{start, end};
    }

    private static Query range(final String field, final long[] range) {
        return BoolQuery.builder().filterClauses(List.of(rangeQuery(field, range))).build();
    }

    private static Query rangeQuery(final String field, final long[] range) {
        return RangeQuery.builder().field(field).from(range[0]).to(range[1]).includeUpper(false).build();
    }

    private static Aggregator weeks(final List<Long> weeks, final long end, final String field,
                                    final Aggregator subAggregator) {
        final var filters = new LinkedHashMap<String, Query>();
        for (var index = 0; index < weeks.size(); index++) {
            filters.put(weekKey(index), RangeQuery.builder().field(field).from(weeks.get(index))
                    .to(index + 1 < weeks.size() ? weeks.get(index + 1) : end).includeUpper(false).build());
        }
        final var builder = FiltersAggregator.builder().name(WEEKS).filters(filters);
        return (subAggregator == null ? builder : builder.subAggregator(subAggregator)).build();
    }

    /**
     * Projection indices are created lazily on first write, so a missing index means there is nothing to count yet.
     */
    private <T> SearchResult<T> search(final String index, final Query query, final TypeReference<T> type,
                                       final List<Sort> sorts, final List<Aggregator> aggregators,
                                       final int size, final int offset) {
        try {
            return storageManager.search(index, query, type, sorts, aggregators, size, offset);
        } catch (RuntimeException e) {
            if (indexMissing(e)) {
                return null;
            }
            throw e;
        }
    }

    private static boolean indexMissing(final Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("index_not_found_exception")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, FiltersAggregation.Bucket> buckets(final SearchResult<?> search, final String name) {
        final var aggregation = search == null || search.getAggregations() == null ? null
                : search.getAggregations().get(name);
        return aggregation instanceof FiltersAggregation filters && filters.getBuckets() != null
                ? filters.getBuckets() : Map.of();
    }

    private static Aggregation sub(final FiltersAggregation.Bucket bucket, final String name) {
        return bucket == null || bucket.getAggregations() == null ? null : bucket.getAggregations().get(name);
    }

    @SuppressWarnings("java:S1144")
    private static Aggregation sub(final TermsAggregation.Bucket bucket, final String name) {
        return bucket == null || bucket.getAggregations() == null ? null : bucket.getAggregations().get(name);
    }

    private static FiltersAggregation.Bucket only(final Aggregation aggregation, final String key) {
        return aggregation instanceof FiltersAggregation filters && filters.getBuckets() != null
                ? filters.getBuckets().get(key) : null;
    }

    private static long count(final FiltersAggregation.Bucket bucket) {
        return bucket == null ? 0 : bucket.getCount();
    }

    private static Map<String, Long> counts(final Aggregation aggregation) {
        final var counts = new LinkedHashMap<String, Long>();
        if (aggregation instanceof TermsAggregation terms && terms.getBuckets() != null) {
            terms.getBuckets().forEach(bucket -> counts.put(String.valueOf(bucket.getKey()), bucket.getCount()));
        }
        return counts;
    }

    /**
     * Terms buckets re-keyed by term, wrapped as filter buckets so callers can share count/sub helpers.
     */
    private static Map<String, FiltersAggregation.Bucket> termBuckets(final Aggregation aggregation) {
        final var buckets = new LinkedHashMap<String, FiltersAggregation.Bucket>();
        if (aggregation instanceof TermsAggregation terms && terms.getBuckets() != null) {
            terms.getBuckets().forEach(bucket -> buckets.put(String.valueOf(bucket.getKey()),
                    FiltersAggregation.Bucket.builder().count(bucket.getCount())
                            .aggregations(bucket.getAggregations()).build()));
        }
        return buckets;
    }

    private static long accepted(final Map<String, Long> acceptance) {
        return acceptance.getOrDefault("applied", 0L) + acceptance.getOrDefault("partially_applied", 0L);
    }

    private static Double rate(final long numerator, final long denominator) {
        return denominator <= 0 ? null : numerator * 100.0 / denominator;
    }

    private static Aggregator terms(final String name, final String field) {
        return TermsAggregator.builder().name(name).field(field).size(MAX_TERMS).build();
    }

    private static Query term(final String field, final Object value) {
        return TermQuery.builder().field(field).value(value).build();
    }

    private static String weekKey(final int index) {
        return "week_" + index;
    }
}
