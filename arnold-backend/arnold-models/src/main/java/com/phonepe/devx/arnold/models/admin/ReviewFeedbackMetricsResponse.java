package com.phonepe.devx.arnold.models.admin;

import java.util.List;
import java.util.Map;

/**
 * Review-feedback outcome metrics split by origin: comments produced by Arnold versus the previous AI review workflow.
 */
public record ReviewFeedbackMetricsResponse(long from, long to, Origin arnold, Origin workflow) {

    /**
     * {@code acceptanceRate} = (applied + partially_applied) / (total - not_applicable); null when nothing actionable.
     * Breakdown maps are keyed by the stored value (e.g. acceptance status, priority, model) and hold comment counts.
     */
    public record Origin(long total, long accepted, long actionable, Double acceptanceRate, Score score,
                         Map<String, Long> acceptance, Map<String, Long> priority, Map<String, Long> commentType,
                         Map<String, Long> suggestion, Map<String, Long> category, Map<String, Long> model,
                         Map<String, Long> persona, Map<String, Long> project, List<Week> weekly) {
    }

    public record Score(long count, Double average, Double min, Double max) {
    }

    public record Week(String weekStart, long total, long accepted, Double acceptanceRate) {
    }
}
