package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.devx.arnold.core.model.analysis.PersonaReviewResult;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeAnalysisRunStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.ModelUsage;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse.Run;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static com.phonepe.devx.arnold.core.services.impl.ReviewFeedbackMetricsService.DAY_MS;

/**
 * Token consumption per analysis run, read from the run stores. Runs expire after {@link #MAX_RANGE_MS}, so older
 * usage is only available from the {@code inputTokens}/{@code outputTokens} fields of the projected run documents.
 */
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class TokenUsageMetricsService {
    static final long MAX_RANGE_MS = 90 * DAY_MS;
    private static final int RECENT_RUNS = 100;

    private final AerospikeAnalysisRunStore runStore;
    private final AerospikeGroupAnalysisRunStore groupRunStore;

    public TokenUsageMetricsResponse metrics(final Long from, final Long to) {
        final var end = to == null ? System.currentTimeMillis() : to;
        final var start = from == null ? end - 30 * DAY_MS : from;
        if (start >= end || end - start > MAX_RANGE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("from must be before to and the range must not exceed 90 days").build();
        }
        // ponytail: full scans, like analysis history; switch to ES aggregations on the run documents if volume grows.
        final var runs = new ArrayList<Usage>();
        runStore.list().stream().filter(run -> run.createdAt() >= start && run.createdAt() < end)
                .map(TokenUsageMetricsService::usage).forEach(runs::add);
        groupRunStore.list().stream()
                .filter(run -> run.result().createdAt() >= start && run.result().createdAt() < end)
                .map(TokenUsageMetricsService::usage).forEach(runs::add);
        return summarize(start, end, runs);
    }

    record Usage(Run run, TokenUsage usage) {
    }

    static TokenUsageMetricsResponse summarize(final long from, final long to, final List<Usage> runs) {
        final var reported = runs.stream().filter(run -> run.usage() != null).toList();
        final var models = new TreeMap<String, long[]>();
        reported.forEach(run -> run.usage().models().forEach(model -> {
            final var totals = models.computeIfAbsent(model.model(), ignored -> new long[3]);
            totals[0]++;
            totals[1] += model.inputTokens();
            totals[2] += model.outputTokens();
        }));
        return new TokenUsageMetricsResponse(from, to, runs.size(), reported.size(),
                reported.stream().mapToLong(run -> run.usage().inputTokens()).sum(),
                reported.stream().mapToLong(run -> run.usage().outputTokens()).sum(),
                models.entrySet().stream().map(entry -> new ModelUsage(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2])).toList(),
                reported.stream().map(Usage::run).sorted(Comparator.comparingLong(Run::createdAt).reversed())
                        .limit(RECENT_RUNS).toList());
    }

    private static Usage usage(final AnalysisRun run) {
        final var usage = TokenUsage.merge(run.results().values().stream().map(PersonaReviewResult::usage).toList());
        return new Usage(row(run.id(), "analysis", run.mergeRequestId(), run.invokedBy(),
                run.execution() == null ? null : run.execution().harness(), run.status(), usage, run.createdAt()), usage);
    }

    private static Usage usage(final GroupAnalysisRun run) {
        final var usage = TokenUsage.merge(run.usageByExecution().values());
        final var execution = run.context() == null ? null : run.context().execution();
        return new Usage(row(run.id(), "group_analysis", run.result().groupName(), run.invokedBy(),
                execution == null ? null : execution.harness(), run.result().runStatus(), usage,
                run.result().createdAt()), usage);
    }

    private static Run row(final String id, final String kind, final String target, final String invokedBy,
                           final String harness, final String status, final TokenUsage usage, final long createdAt) {
        return new Run(id, kind, target, invokedBy, harness, status, usage == null ? 0 : usage.inputTokens(),
                usage == null ? 0 : usage.outputTokens(), createdAt);
    }
}
