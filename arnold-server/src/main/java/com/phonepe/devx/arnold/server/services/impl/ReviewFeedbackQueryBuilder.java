package com.phonepe.devx.arnold.server.services.impl;

import com.phonepe.commons.columbus.search.query.Query;
import com.phonepe.commons.columbus.search.query.compound.BoolQuery;
import com.phonepe.commons.columbus.search.query.term.PrefixQuery;
import com.phonepe.commons.columbus.search.query.term.RangeQuery;
import com.phonepe.commons.columbus.search.query.term.TermQuery;
import com.phonepe.commons.columbus.search.query.term.TermsQuery;
import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.core.models.feedback.TargetScope;
import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.Priority;

import javax.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ReviewFeedbackQueryBuilder {

    private ReviewFeedbackQueryBuilder() {
    }

    static Query build(final FeedbackQueryParams params) {
        final List<Query> filterClauses = new ArrayList<>();

        addStatusFilter(filterClauses, params.getStatuses());
        addScoreFilter(filterClauses, params.getMinScore());
        addTimeRangeFilter(filterClauses, params.getDaysBack());
        addScopeFilter(filterClauses, params.getScope(), params.getTargetPath());
        addTeamFilter(filterClauses, params.getTeamName());
        addPriorityFilter(filterClauses, params.getMinPriority());

        return BoolQuery.builder()
                .filterClauses(filterClauses)
                .build();
    }

    private static void addStatusFilter(final List<Query> clauses, final List<AcceptanceStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return;
        }
        clauses.add(TermsQuery.builder()
                .field("acceptanceStatus")
                .values(statuses.stream().map(AcceptanceStatus::getValue).toList())
                .build());
    }

    private static void addScoreFilter(final List<Query> clauses, final Double minScore) {
        if (minScore != null) {
            clauses.add(RangeQuery.builder()
                    .field("score")
                    .from(minScore)
                    .includeLower(true)
                    .build());
        }
    }

    private static void addTimeRangeFilter(final List<Query> clauses, final int daysBack) {
        final long cutoffMs = Instant.now().minus(daysBack, ChronoUnit.DAYS).toEpochMilli();
        clauses.add(RangeQuery.builder()
                .field("ingestedAt")
                .from(cutoffMs)
                .includeLower(true)
                .build());
    }

    private static void addScopeFilter(
            final List<Query> clauses,
            final TargetScope scope,
            final String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            return;
        }

        final var effectiveScope = scope == null ? TargetScope.GROUP : scope;

        switch (effectiveScope) {
            case GROUP, SUBGROUP -> clauses.add(namespaceSubtreeQuery(targetPath));

            case PROJECT -> {
                final int lastSlash = targetPath.lastIndexOf('/');
                if (lastSlash > 0 && lastSlash < targetPath.length() - 1) {
                    final String namespace = targetPath.substring(0, lastSlash);
                    final String projectName = targetPath.substring(lastSlash + 1);
                    clauses.add(TermQuery.builder()
                            .field("project.projectNamespace")
                            .value(namespace)
                            .build());
                    clauses.add(TermQuery.builder()
                            .field("project.projectName")
                            .value(projectName)
                            .build());
                } else {
                    throw new BadRequestException(
                            "PROJECT scope requires a fully-qualified path (namespace/projectName), got: "
                                    + targetPath);
                }
            }
        }
    }

    private static void addTeamFilter(final List<Query> clauses, final String teamName) {
        if (teamName != null && !teamName.isBlank()) {
            clauses.add(TermQuery.builder()
                    .field("project.teamId")
                    .value(teamName)
                    .build());
        }
    }

    private static void addPriorityFilter(final List<Query> clauses, final Priority minPriority) {
        if (minPriority != null) {
            clauses.add(TermsQuery.builder()
                    .field("priority")
                    .values(Arrays.stream(Priority.values())
                            .filter(priority -> priority.getRank() <= minPriority.getRank())
                            .map(Priority::getValue)
                            .toList())
                    .build());
        }
    }

    private static Query namespaceSubtreeQuery(final String targetPath) {
        final var namespacePrefix = targetPath.endsWith("/") ? targetPath : targetPath + "/";
        return BoolQuery.builder()
                .minimumMatch("1")
                .should(TermQuery.builder()
                        .field("project.projectNamespace")
                        .value(targetPath)
                        .build())
                .should(PrefixQuery.builder()
                        .field("project.projectNamespace")
                        .value(namespacePrefix)
                        .build())
                .build();
    }
}
