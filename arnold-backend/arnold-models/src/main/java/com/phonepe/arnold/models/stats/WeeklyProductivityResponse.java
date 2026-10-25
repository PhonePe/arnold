package com.phonepe.arnold.models.stats;

public record WeeklyProductivityResponse(
        String weekStart,
        long authoredMerged,
        long reviewWorkCompleted
) {
}
