package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.commons.columbus.search.aggregation.bucket.FiltersAggregation;
import com.phonepe.commons.columbus.search.aggregation.metrics.StatsAggregation;
import com.phonepe.commons.columbus.search.aggregator.Aggregator;
import com.phonepe.commons.columbus.search.aggregator.bucket.FiltersAggregator;
import com.phonepe.commons.columbus.search.aggregator.metrics.StatsAggregator;
import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.simple.MatchAllQuery;
import com.phonepe.commons.columbus.search.query.term.FieldExistsQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.commons.columbus.search.sort.FieldSort;
import com.phonepe.commons.columbus.search.sort.SortOrder;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import com.phonepe.devx.arnold.core.elastic.SearchIndexReferences;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.StatsService;
import com.phonepe.devx.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.devx.arnold.models.es.MergeRequestDocument;
import com.phonepe.devx.arnold.models.stats.StatsResponse;
import com.phonepe.devx.arnold.models.stats.WeeklyProductivityResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class StatsServiceImpl implements StatsService {
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    private static final String COUNTS_AGGREGATION = "counts";
    private static final String DATA_THROUGH_AGGREGATION = "data_through";
    private static final String OPEN_AUTHORED = "open_authored";
    private static final String OPEN_REVIEW_WORK = "open_review_work";
    private static final String MERGED = "merged";
    private static final String CURRENT_THROUGHPUT = "current_throughput";
    private static final String PREVIOUS_THROUGHPUT = "previous_throughput";

    private final ESStorageManager storageManager;
    private final VersionControlIntegrationService integrationService;

    @Inject
    public StatsServiceImpl(final ESStorageManager storageManager,
                            final VersionControlIntegrationService integrationService) {
        this.storageManager = storageManager;
        this.integrationService = integrationService;
    }

    @Override
    public StatsResponse personal(final String userId) {
        final var participants = new EnumMap<VersionControlProviderType, Long>(VersionControlProviderType.class);
        Arrays.stream(VersionControlProviderType.values()).forEach(provider -> {
            final var integration = integrationService.status(provider, userId);
            if (integration.connected() && integration.providerUserId() != null) {
                participants.put(provider, Long.parseLong(integration.providerUserId()));
            }
        });
        if (participants.isEmpty()) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                    .message("No version control integration is connected").build();
        }
        return aggregate("personal", participantQuery(participants), participants);
    }

    @Override
    public StatsResponse organization() {
        return aggregate("organization", MatchAllQuery.builder().build(), Map.of());
    }

    private StatsResponse aggregate(final String scope,
                                    final Query scopeQuery,
                                    final Map<VersionControlProviderType, Long> participants) {
        final var now = System.currentTimeMillis();
        final var currentWeekStart = weekStart(now);
        final var scopedQuery = and(scopeQuery, notArchived());
        final List<Aggregator> aggregators = List.of(
                FiltersAggregator.builder().name(COUNTS_AGGREGATION)
                        .filters(filters(participants, currentWeekStart)).build(),
                StatsAggregator.builder().name(DATA_THROUGH_AGGREGATION).field("lastSyncedAt").build());
        final var search = storageManager.search(SearchIndexReferences.CHANGE_REQUESTS, scopedQuery,
                new TypeReference<MergeRequestDocument>() { }, List.of(), aggregators, 0, 0);
        final var counts = search == null || search.getAggregations() == null ? null
                : (FiltersAggregation) search.getAggregations().get(COUNTS_AGGREGATION);
        final var buckets = counts == null ? Map.<String, FiltersAggregation.Bucket>of() : counts.getBuckets();
        final var merged = count(buckets, MERGED);
        final var currentThroughput = count(buckets, CURRENT_THROUGHPUT);
        final var previousThroughput = count(buckets, PREVIOUS_THROUGHPUT);
        final Double delta = previousThroughput == 0 ? null
                : (currentThroughput - previousThroughput) * 100.0 / previousThroughput;
        final var dataThroughStats = search == null || search.getAggregations() == null ? null
                : (StatsAggregation) search.getAggregations().get(DATA_THROUGH_AGGREGATION);
        final var dataThrough = dataThroughStats == null || dataThroughStats.getCount() == 0
                ? 0 : (long) dataThroughStats.getMaximum();
        return new StatsResponse(scope, count(buckets, OPEN_AUTHORED), count(buckets, OPEN_REVIEW_WORK),
                merged, median(scopedQuery, merged), delta, weekly(buckets, currentWeekStart), now, dataThrough);
    }

    private Map<String, Query> filters(final Map<VersionControlProviderType, Long> participants,
                                       final long currentWeekStart) {
        final var organization = participants.isEmpty();
        final var filters = new LinkedHashMap<String, Query>();
        filters.put(OPEN_AUTHORED, and(openQuery(), organization
                ? MatchAllQuery.builder().build() : identityQuery("authorId", participants)));
        filters.put(OPEN_REVIEW_WORK, and(openQuery(), organization
                ? FieldExistsQuery.builder().field("reviewerIds").build()
                : either(identityQuery("reviewerIds", participants), identityQuery("assigneeIds", participants))));
        filters.put(MERGED, mergedQuery());
        filters.put(CURRENT_THROUGHPUT, range("mergedAt", currentWeekStart, null));
        filters.put(PREVIOUS_THROUGHPUT, range("mergedAt", currentWeekStart - WEEK_MS, currentWeekStart));
        for (var offset = 6; offset > 0; offset--) {
            final var index = 6 - offset;
            final var start = currentWeekStart - offset * WEEK_MS;
            final var end = start + WEEK_MS;
            filters.put(weekKey(index, "authored"), organization
                    ? range("mergedAt", start, end)
                    : and(range("mergedAt", start, end), identityQuery("authorId", participants)));
            final var terminal = either(range("mergedAt", start, end), range("closedAt", start, end));
            filters.put(weekKey(index, "reviewed"), organization
                    ? and(terminal, FieldExistsQuery.builder().field("reviewerIds").build())
                    : and(terminal, identityQuery("reviewerIds", participants)));
        }
        return filters;
    }

    private List<WeeklyProductivityResponse> weekly(final Map<String, FiltersAggregation.Bucket> buckets,
                                                    final long currentWeekStart) {
        final var weeks = new ArrayList<WeeklyProductivityResponse>();
        for (var offset = 6; offset > 0; offset--) {
            final var start = currentWeekStart - offset * WEEK_MS;
            final var index = 6 - offset;
            weeks.add(new WeeklyProductivityResponse(Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
                    .toLocalDate().toString(), count(buckets, weekKey(index, "authored")),
                    count(buckets, weekKey(index, "reviewed"))));
        }
        return List.copyOf(weeks);
    }

    private long median(final Query scopeQuery, final long mergedCount) {
        if (mergedCount == 0) {
            return 0;
        }
        final var offset = (int) Math.min(Integer.MAX_VALUE, (mergedCount - 1) / 2);
        final var size = mergedCount % 2 == 0 ? 2 : 1;
        final var result = storageManager.search(SearchIndexReferences.CHANGE_REQUESTS, and(scopeQuery, mergedQuery()),
                new TypeReference<MergeRequestDocument>() { },
                List.of(FieldSort.builder().field("mergeDurationMs").order(SortOrder.ASC).build()), size, offset);
        if (result == null || result.getResult() == null || result.getResult().getHits() == null
                || result.getResult().getHits().isEmpty()) {
            return 0;
        }
        final var durations = result.getResult().getHits().stream()
                .map(hit -> hit.getEntity().mergeDurationMs()).toList();
        return durations.size() == 1 ? durations.get(0)
                : Math.round((durations.get(0) + durations.get(1)) / 2.0);
    }

    private Query participantQuery(final Map<VersionControlProviderType, Long> participants) {
        return either(participants.entrySet().stream()
                .map(entry -> and(term("provider.keyword", entry.getKey().name()),
                        either(term("authorId", entry.getValue()), term("assigneeIds", entry.getValue()),
                                term("reviewerIds", entry.getValue()))))
                .toArray(Query[]::new));
    }

    private Query identityQuery(final String field, final Map<VersionControlProviderType, Long> participants) {
        return either(participants.entrySet().stream()
                .map(entry -> and(term("provider.keyword", entry.getKey().name()),
                        term(field, entry.getValue())))
                .toArray(Query[]::new));
    }

    private Query openQuery() {
        return TermsQuery.builder().field("state.keyword").value("opened").value("open").build();
    }

    private Query mergedQuery() {
        return range("mergedAt", 1L, null);
    }

    private Query notArchived() {
        return BoolQuery.builder().mustNot(term("projectArchived", true)).build();
    }

    private Query term(final String field, final Object value) {
        return TermQuery.builder().field(field).value(value).build();
    }

    private Query range(final String field, final Object from, final Object to) {
        return RangeQuery.builder().field(field).from(from).to(to).includeUpper(false).build();
    }

    private Query and(final Query... queries) {
        return BoolQuery.builder().filterClauses(List.of(queries)).build();
    }

    private Query either(final Query... queries) {
        return BoolQuery.builder().shouldClauses(List.of(queries)).minimumMatch("1").build();
    }

    private long weekStart(final long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private long count(final Map<String, FiltersAggregation.Bucket> buckets, final String key) {
        final var bucket = buckets.get(key);
        return bucket == null ? 0 : bucket.getCount();
    }

    private String weekKey(final int index, final String metric) {
        return "week_" + index + "_" + metric;
    }
}
