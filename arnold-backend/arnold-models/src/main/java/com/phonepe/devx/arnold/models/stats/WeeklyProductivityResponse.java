package com.phonepe.devx.arnold.models.stats;

public record WeeklyProductivityResponse(
        String weekStart,
        long authoredMerged,
        long reviewWorkCompleted
) {
}
