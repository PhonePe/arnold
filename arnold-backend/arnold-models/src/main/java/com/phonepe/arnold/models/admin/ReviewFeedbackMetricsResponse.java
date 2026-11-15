/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.models.admin;

import java.util.List;
import java.util.Map;

/**
 * Review-feedback outcome metrics split by origin (Arnold versus the previous AI review workflow), plus the AI versus
 * human split of all published MR comments. {@code personaSplit} divides the Arnold origin into feedback attributed
 * to a review persona and general (unattributed) feedback.
 */
public record ReviewFeedbackMetricsResponse(long from, long to, Origin arnold, Origin workflow,
                                            Authorship authorship, PersonaSplit personaSplit, Runs runs,
                                            SmartDiffs smartDiffs, Engagement engagement, GroupRuns groupRuns) {

    /**
     * Smart diffs requested in the range. {@code status} counts them by stored status
     * ({@code completed} / {@code failed}), so successes and failures can be read off it.
     */
    public record SmartDiffs(long total, Map<String, Long> status, Map<String, Long> assessment,
                             Double averageDurationMs, long totalTokens, List<CommentWeek> weekly) {
    }

    /**
     * Merge request group analysis runs created in the range; {@code groups} and {@code invokers} are distinct
     * counts so adoption can be read separately from volume.
     */
    public record GroupRuns(long total, long groups, long invokers, Map<String, Long> status,
                            Double averageDurationMs, long findings, long postedFindings,
                            long totalTokens, List<CommentWeek> weekly) {
    }

    /**
     * Arnold analysis runs created in the range. Breakdown maps are keyed by the stored
     * value and hold run counts; token and finding figures are sums.
     */
    public record Runs(long total, Map<String, Long> outcome, Map<String, Long> status,
                       Double averageDurationMs, long findings, long postedFindings,
                       long totalTokens, List<CommentWeek> weekly) {
    }

    /**
     * Reviewer ratings of Arnold focus areas. {@code focusUsefulRate} = useful / rated.
     */
    public record Engagement(long reviewers, long mergeRequests, long focusAreasGenerated, long focusAreasRated, long focusAreasUseful, long focusAreasNotUseful,
                             Double focusUsefulRate, List<FocusArea> areas, List<FocusWeek> weekly) {
    }

    public record FocusArea(String area, long useful, long notUseful, Double usefulRate) {
    }

    public record FocusWeek(String weekStart, long useful, long notUseful) {
    }

    public record PersonaSplit(Origin persona, Origin general) {
    }

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

    /**
     * Published, non-deleted MR comments in the range. Comments by {@code aiAuthor} are AI, every other author is
     * human. Only the time range applies; comments carry no project, model or persona.
     */
    public record Authorship(String aiAuthor, Authors ai, Authors human) {
    }

    /**
     * Resolvable comments only; {@code total} equals {@code resolvable}. {@code resolutionRate} = resolved / resolvable.
     * {@code feedback} holds the acceptance, category, score and weekly breakdowns of review feedback written by this
     * author type (reviewer = {@code aiAuthor} is AI, everyone else is human).
     */
    public record Authors(long total, long resolvable, long resolved, Double resolutionRate, long mergeRequests,
                          long byMergeRequestAuthor, List<CommentWeek> weekly, Origin feedback) {
    }

    public record CommentWeek(String weekStart, long total) {
    }
}
