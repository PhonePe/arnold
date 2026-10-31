package com.phonepe.devx.arnold.models.persona;

import java.util.List;

/**
 * Effectiveness of one review persona over {@code [from, to)}. Rates are percentages; null when there is nothing to
 * divide by. {@code acceptanceRate} = (applied + partially_applied) / (feedback - not_applicable).
 */
public record PersonaMetricsResponse(String personaId, long from, long to, long runs, Findings findings,
                                     Acceptance acceptance, Resolution resolution, List<Week> weekly,
                                     List<Version> versions) {

    public record Findings(long total, long posted, long failed) {
    }

    public record Acceptance(long feedback, long accepted, long actionable, Double acceptanceRate) {
    }

    public record Resolution(long comments, long resolvable, long resolved, Double resolutionRate) {
    }

    public record Week(String weekStart, long findings, long feedback, long accepted, Double acceptanceRate) {
    }

    public record Version(long version, long runs, long findings, long feedback, long accepted,
                          Double acceptanceRate) {
    }
}
