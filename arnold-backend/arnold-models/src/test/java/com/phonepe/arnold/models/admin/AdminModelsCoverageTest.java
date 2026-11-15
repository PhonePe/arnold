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

import com.phonepe.arnold.models.analysis.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminModelsCoverageTest {

    @Test
    void testReviewFeedbackMetricsResponse_shouldExposeComponents_whenBuilt() {
        final var score = new ReviewFeedbackMetricsResponse.Score(1, 2.0, 1.0, 3.0);
        final var origin = new ReviewFeedbackMetricsResponse.Origin(10, 4, 8, 50.0, score, Map.of("a", 1L),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(new ReviewFeedbackMetricsResponse.Week("w", 5, 2, 40.0)));
        final var authors = new ReviewFeedbackMetricsResponse.Authors(9, 8, 4, 50.0, 3, 2,
                List.of(new ReviewFeedbackMetricsResponse.CommentWeek("w", 9)), origin);
        final var response = new ReviewFeedbackMetricsResponse(1, 2, origin, origin,
                new ReviewFeedbackMetricsResponse.Authorship("bot", authors, authors),
                new ReviewFeedbackMetricsResponse.PersonaSplit(origin, origin), null, null, null, null);

        assertEquals(3.0, response.arnold().score().max());
        assertEquals("w", response.arnold().weekly().get(0).weekStart());
        assertEquals(9, response.authorship().ai().weekly().get(0).total());
        assertEquals(50.0, response.authorship().human().resolutionRate());
        assertEquals(1L, (response.personaSplit().general().acceptance()).get("a"));
    }

    @Test
    void testReviewFeedbackMetricsResponse_shouldExposeActivitySections_whenBuilt() {
        final var weekly = List.of(new ReviewFeedbackMetricsResponse.CommentWeek("w", 2));
        final var runs = new ReviewFeedbackMetricsResponse.Runs(4, Map.of("SUCCEEDED", 4L), Map.of(), 2_000.0, 12, 9,
                500, weekly);
        final var smartDiffs = new ReviewFeedbackMetricsResponse.SmartDiffs(3, Map.of("completed", 3L), Map.of(),
                null, 40, weekly);
        final var engagement = new ReviewFeedbackMetricsResponse.Engagement(5, 2, 3, 4, 3, 1, 75.0,
                List.of(new ReviewFeedbackMetricsResponse.FocusArea("tests", 3, 1, 75.0)),
                List.of(new ReviewFeedbackMetricsResponse.FocusWeek("w", 3, 1)));
        final var groupRuns = new ReviewFeedbackMetricsResponse.GroupRuns(2, 1, 1, Map.of(), 1_500.0, 6, 5, 80, weekly);
        final var response = new ReviewFeedbackMetricsResponse(1, 2, null, null, null, null, runs, smartDiffs,
                engagement, groupRuns);

        assertEquals(9, response.runs().postedFindings());
        assertNull(response.smartDiffs().averageDurationMs());
        assertEquals("tests", response.engagement().areas().get(0).area());
        assertEquals(1, response.engagement().weekly().get(0).notUseful());
        assertEquals(5, response.groupRuns().postedFindings());
    }

    @Test
    void testTokenUsageMetricsResponse_shouldExposeComponents_whenBuilt() {
        final var usage = new TokenUsage(1, 2, List.of());
        final var response = new TokenUsageMetricsResponse(1, 2, 3, 4, 5, 6,
                List.of(new TokenUsageMetricsResponse.FlowUsage("improve", 1, 2, 3, 4)),
                List.of(new TokenUsageMetricsResponse.ModelUsage("m", 1, 2, 3, 4)),
                List.of(new TokenUsageMetricsResponse.Job("id", "f", "r", "t", "u", "h", "s", usage, 1, 2)));

        assertEquals("improve", response.flows().get(0).flow());
        assertEquals("m", response.models().get(0).model());
        assertSame(usage, response.recentJobs().get(0).usage());
        assertEquals(6, response.cacheReadTokens());
    }
}
