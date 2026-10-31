package com.phonepe.devx.arnold.models.stats;

import java.util.List;

public record StatsResponse(
        String scope,
        long openAuthored,
        long openReviewWork,
        long merged,
        long medianTimeToMergeMs,
        Double throughputDeltaPct,
        List<WeeklyProductivityResponse> weekly,
        long generatedAt,
        long dataThrough
) {
    public StatsResponse {
        weekly = weekly == null ? List.of() : List.copyOf(weekly);
    }
}
